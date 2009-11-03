package fakehttp

import java.net.InetSocketAddress
import org.jboss.netty.buffer.ChannelBuffer
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
import org.jboss.netty.handler.ssl.SslHandler
import fakehttp.Implicits._
import fakehttp.ssl.FakeSsl
import fakehttp.handler._

/**
 * Gets an HttpRequest from the browser, sets up a connection to the
 * right server and hooks the two together.
 */
@ChannelPipelineCoverage("one")
class IncomingRequestHandler(
  id: Int,
  requestInterceptor: HttpHandler,
  incomingPipelineFactory: IncomingPipelineFactory,
  outgoingChannelFactory: ClientSocketChannelFactory
  ) extends SimpleChannelUpstreamHandler {

  @volatile private var incomingChannel: Channel = null
  @volatile private var outgoingChannel: Channel = null
  @volatile private var lastHost: String = null

  override def channelOpen(cxt: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    incomingChannel = e.getChannel
  }

  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    // If we're getting raw ChannelBuffers, our HttpMessageDecoder has been removed
    // for opaque SSL mode, so just forward on the raw bytes
    if (e.getMessage.isInstanceOf[ChannelBuffer]) {
      log("Got "+e.getMessage+" for "+lastHost)
      sendDownstream(outgoingChannel, e.getMessage)
      return
    }

    val req = e.getMessage.asInstanceOf[HttpRequest]
    log("Got "+req.getMethod+" request for "+req.getUri)
    requestInterceptor.handle(req) match {
      case ProxyResult(host, port) => sendProxy(host, port, req)
      case StaticResult(response) => sendDownstream(incomingChannel, response)
    }
  }

  def outgoingConnectionComplete(newOutgoingChannel: Channel, initialBrowserRequest: HttpRequest): Unit = {
    if (!newOutgoingChannel.isConnected) {
      log("Outgoing connection failed "+newOutgoingChannel)
      safelyCloseChannels
      return
    }

    outgoingChannel = newOutgoingChannel

    val in = incomingChannel
    if (in == null) { safelyCloseChannels ; return }

    if (initialBrowserRequest.getMethod == HttpMethod.CONNECT) {
      setupIncomingForSsl(lastHost)
    } else {
      sendDownstream(newOutgoingChannel, initialBrowserRequest)
    }
    in.setReadable(true)
  }
  
  def outgoingResponseReceived(response: Object): Unit = {
    // log("Outgoing responded "+response)
    sendDownstream(incomingChannel, response)
  }

  def outgoingChannelClosed(): Unit = {
    log("Outgoing channel closed")
    outgoingChannel = null
  }

  def outgoingException(e: ExceptionEvent): Unit = {
    log("Outgoing exception "+e.getCause)
    e.getCause.printStackTrace
    safelyCloseChannels
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    log("Incoming exception "+e.getCause)
    e.getCause.printStackTrace
    safelyCloseChannels
  }
  
  override def channelClosed(cxt: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    log("Incoming channel closed")
    incomingPipelineFactory.handlerClosed(this)
    incomingChannel = null
    safelyCloseChannels
  }

  def safelyCloseChannels(): Unit = {
    val in = incomingChannel
    val out = outgoingChannel
    incomingChannel = null
    outgoingChannel = null
    if (in != null && in.isOpen) in.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    if (out != null && out.isOpen) out.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
  }

  private def sendProxy(host: String, port: Int, req: HttpRequest): Unit = {
    val out = outgoingChannel
    if (out == null) {
      log("Outgoing host initialized as "+host+" on "+port)
      createOutgoingChannel(host, port, req)
    } else if (host != lastHost) {
      log("Outgoing host changed to "+host+" on "+port)
      out.close.addListener((future: ChannelFuture) => {
        outgoingChannel = null
        createOutgoingChannel(host, port, req)
      })
    } else {
      sendDownstream(out, req)
    }
  }

  private def createOutgoingChannel(host: String, port: Int, initialBrowserRequest: HttpRequest): Unit = {
    val in = incomingChannel
    if (in == null) return

    // No browser input until proxy is connected
    in.setReadable(false)

    val outgoingPipeline = Channels.pipeline()
    if (initialBrowserRequest.getMethod() == HttpMethod.CONNECT) {
      // Our outgoing prSxy is an SSL client--we'll setup our SSL server on in proxyConnectionComplete
      val clientEngine = FakeSsl.clientContext.createSSLEngine
      clientEngine.setUseClientMode(true)
      // outgoingPipeline.addLast("ssl", new SslHandler(clientEngine))
    }

    lastHost = host

    outgoingPipeline.addFirst("connector", new OutgoingConnectHandler(this, new InetSocketAddress(host, port), initialBrowserRequest))
    // put these back if you want to translate the response somehow
    // outgoingPipeline.addLast("decoder", new HttpResponseDecoder())
    // outgoingPipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    outgoingPipeline.addLast("encoder", new HttpRequestEncoder())
    outgoingPipeline.addLast("outgoingResponse", new OutgoingResponseHandler(this))
    outgoingChannelFactory.newChannel(outgoingPipeline)
  }

  private def sendDownstream(channel: Channel, message: Object): Unit = {
    sendDownstream(channel, message, null)
  }

  private def sendDownstream(channel: Channel, message: Object, onSuccess: ChannelFuture => Unit): Unit = {
    if (channel == null) return
    val ioDone = Channels.future(channel)
    ioDone.addListener((future: ChannelFuture) => {
      if (!future.isSuccess) {
        log("I/O error sending to "+channel)
        safelyCloseChannels
      } else {
        if (onSuccess != null) onSuccess(future)
      }
    })
    channel.getPipeline.sendDownstream(new DownstreamMessageEvent(channel, ioDone, message, null))
  }

  private def log(message: String): Unit = {
    System.err.println(id+" - "+message)
  }

  /** After receiving a CONNECT request, responds with HTTP 200 and then installs an SSLHandler for the host. */
  private def setupIncomingForSsl(host: String): Unit = {
    // val serverEngine = FakeSsl.createServerContextForHost(host).createSSLEngine
    val buffer = ChannelBuffers.wrappedBuffer("HTTP/1.1 200 Connection established\r\n\r\n".getBytes())
    sendDownstream(incomingChannel, buffer, (future: ChannelFuture) => {
      incomingChannel.getPipeline.remove("decoder")
      incomingChannel.getPipeline.remove("aggregator")
      // incomingChannel.getPipeline.addFirst("ssl", new SslHandler(serverEngine))
    })
  }
}
