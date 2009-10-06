package fakehttp

import java.net.InetSocketAddress
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelPipelineCoverage
import org.jboss.netty.channel.ChannelStateEvent
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.DownstreamMessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.channel.socket.ClientSocketChannelFactory
import org.jboss.netty.handler.codec.http._
import fakehttp.Implicits._
import fakehttp.ssl.FakeSsl

/**
 * Gets an HttpRequest from the browser, sets up a connection to the
 * right server and hooks the two together.
 */
@ChannelPipelineCoverage("one")
class ServerBrowserRequestHandler(
  val id: Int,
  serverPipelineFactory: ServerPipelineFactory,
  clientChannelFactory: ClientSocketChannelFactory)
  extends SimpleChannelUpstreamHandler with Comparable[ServerBrowserRequestHandler] {

  @volatile private var browserChannel: Channel = null
  @volatile private var proxyChannel: Channel = null
  @volatile private var lastHost: String = null
  @volatile private var isSsl: Boolean = false

  override def channelOpen(cxt: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    browserChannel = e.getChannel
  }

  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    val browserRequest = e.getMessage.asInstanceOf[HttpRequest]
    val host = browserRequest.getHeader(HttpHeaders.Names.HOST) match {
      case "fakehttp" => "fakehttp"
      case s => s // "localhost"
    }
    val port = browserRequest.getHeader(HttpHeaders.Names.HOST) match {
      case "fakehttp" => 443
      case p => 80
    }

    log("Got request for "+browserRequest.getUri+" (host="+host+") (method="+browserRequest.getMethod+")")
    Traffic.record(browserRequest.getUri)

    if (host == "fakehttp") {
      if (browserRequest.getMethod == HttpMethod.CONNECT) {
        setupBrowserChannelSsl(host)
      } else {
        handleFakeHttpCall(browserRequest)
      }
    } else if (proxyChannel != null) {
      if (host == lastHost) {
        sendDownstream(proxyChannel, browserRequest)
      } else {
        log("Proxy host changed to "+host)
        proxyChannel.close.addListener((future: ChannelFuture) => {
          createProxyChannel(host, port, browserRequest)
        })
      }
    } else {
      log("Proxy host initialized as "+host)
      createProxyChannel(host, port, browserRequest)
    }
  }
  
  def proxyConnectionComplete(newProxyChannel: Channel, initialBrowserRequest: HttpRequest): Unit = {
    if (!newProxyChannel.isConnected) {
      log("Proxy connection failed "+newProxyChannel)
      safelyCloseChannels
    } else {
      proxyChannel = newProxyChannel
      if (initialBrowserRequest.getMethod == HttpMethod.CONNECT) {
        setupBrowserChannelSsl(lastHost)
      } else {
        if (!isSsl && browserChannel.getPipeline.get("ssl") != null) {
          log("Removing old ssl from browser channel...")
          browserChannel.getPipeline.remove("ssl")
        }
        sendDownstream(proxyChannel, initialBrowserRequest)
      }
      browserChannel.setReadable(true)
    }
  }
  
  def proxyResponseReceived(response: HttpResponse): Unit = {
    log("Proxy responded "+response)
    sendDownstream(browserChannel, response)
  }

  def proxyChannelClosed(): Unit = {
    log("Proxy channel closed")
    proxyChannel = null
  }

  def proxyException(e: ExceptionEvent): Unit = {
    log("Proxy exception "+e.getCause)
    e.getCause.printStackTrace
    safelyCloseChannels
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    log("Browser exception "+e.getCause)
    e.getCause.printStackTrace
    safelyCloseChannels
  }
  
  override def channelClosed(cxt: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    log("Browser channel closed")
    serverPipelineFactory.browserRequestHanderClosed(this)
    browserChannel = null
    safelyCloseChannels
  }

  def safelyCloseChannels(): Unit = {
    val b = browserChannel
    val p = proxyChannel
    browserChannel = null
    proxyChannel = null
    if (b != null && b.isOpen) b.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    if (p != null && p.isOpen) p.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
  }
  
  override def compareTo(other: ServerBrowserRequestHandler): Int = {
    id - other.id
  }

  private def createProxyChannel(host: String, port: Int, initialBrowserRequest: HttpRequest): Unit = {
    val proxyPipeline = Channels.pipeline()

    if (port == 443) {
      // Our outgoing proxy is an SSL client--we'll setup our SSL server on in proxyConnectionComplete
      import org.jboss.netty.handler.ssl.SslHandler
      val clientEngine = FakeSsl.clientContext.createSSLEngine
      clientEngine.setUseClientMode(true)
      proxyPipeline.addLast("ssl", new SslHandler(clientEngine))
      isSsl = true
    } else {
      isSsl = false
    }
    lastHost = host

    proxyPipeline.addFirst("connector", new ProxyConnectorHandler(this, new InetSocketAddress(host, port), initialBrowserRequest))
    proxyPipeline.addLast("decoder", new HttpResponseDecoder())
    proxyPipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    proxyPipeline.addLast("encoder", new HttpRequestEncoder())
    proxyPipeline.addLast("handler", new ProxyResponseHandler(this))
    // No browser input until proxy is connected
    browserChannel.setReadable(false)
    clientChannelFactory.newChannel(proxyPipeline)
  }

  private def sendDownstream(channel: Channel, message: Object): Unit = {
    if (channel == null) return
    val ioDone = Channels.future(channel)
    ioDone.addListener((future: ChannelFuture) => {
      if (!future.isSuccess) { log("I/O error sending to "+channel) ; safelyCloseChannels }
    })
    channel.getPipeline.sendDownstream(new DownstreamMessageEvent(channel, ioDone, message, null))
  }

  private def sendDownstream(channel: Channel, message: Object, onSuccess: ChannelFuture => Unit): Unit = {
    if (channel == null) return
    val ioDone = Channels.future(channel)
    ioDone.addListener((future: ChannelFuture) => {
      if (!future.isSuccess) {
        log("I/O error sending to "+channel)
        safelyCloseChannels
      } else {
        onSuccess(future)
      }
    })
    channel.getPipeline.sendDownstream(new DownstreamMessageEvent(channel, ioDone, message, null))
  }

  private def log(message: String): Unit = {
    System.err.println(id+" - "+message)
  }
  
  private def handleFakeHttpCall(browserRequest: HttpRequest): Unit = {
    val responseXml = FakeHttpHandler.handle(browserRequest)
    val responseBytes = responseXml.toString.getBytes("UTF-8")
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Server", "fakehttp")
    response.setHeader("Content-Length", responseBytes.length.toString)
    response.setContent(ChannelBuffers.wrappedBuffer(responseBytes))
    sendDownstream(browserChannel, response)
  }

  private def setupBrowserChannelSsl(host: String): Unit = {
    import org.jboss.netty.handler.ssl.SslHandler
    val serverEngine = FakeSsl.createServerContextForHost(host).createSSLEngine
    serverEngine.setUseClientMode(false)
    val buffer = ChannelBuffers.wrappedBuffer("HTTP/1.0 200 Connection established\r\n\r\n".getBytes())
    sendDownstream(browserChannel, buffer, (future: ChannelFuture) => {
      browserChannel.getPipeline.addFirst("ssl", new SslHandler(serverEngine))
    })
  }
}
