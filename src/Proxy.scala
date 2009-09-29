
import java.net.InetSocketAddress
import java.util.concurrent._
import java.util.concurrent.atomic._
import org.jboss.netty.bootstrap._
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.channel.socket._
import org.jboss.netty.channel.socket.nio._
import Implicits._

object Proxy {
  def main(args: Array[String]): Unit = {
    val bossThreadPool = Executors.newCachedThreadPool()
    val workerThreadPool = Executors.newCachedThreadPool()
    val clientChannelFactory = new NioClientSocketChannelFactory(bossThreadPool, workerThreadPool)
    val serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(bossThreadPool, workerThreadPool))
    serverBootstrap.setPipelineFactory(new ServerPipelineFactory(clientChannelFactory));
    serverBootstrap.bind(new InetSocketAddress(8081));
  }
}

object Traffic {
  private val map = new ConcurrentHashMap[String, AtomicInteger]()
  def record(uri: String): Unit = {
    var i = map.putIfAbsent(uri, new AtomicInteger)
    if (i == null) i = map.get(uri)
    i.incrementAndGet
  }
  def hits = scala.collection.jcl.Set(map.entrySet)
}

class ServerPipelineFactory(clientChannelFactory: ClientSocketChannelFactory) extends ChannelPipelineFactory {
  var id = new AtomicInteger
  def getPipeline(): ChannelPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("decoder", new HttpRequestDecoder(4096 * 4, 8192, 8192))
    pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    pipeline.addLast("encoder", new HttpResponseEncoder())
    pipeline.addLast("handler", new BrowserRequestHandler(id.getAndIncrement, clientChannelFactory))
    return pipeline
  }
}

@ChannelPipelineCoverage("one")
class ProxyConnectorHandler(browserRequestHandler: BrowserRequestHandler, socketAddress: InetSocketAddress, initialBrowserRequest: HttpRequest) extends SimpleChannelUpstreamHandler {
  override def channelOpen(context: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    e.getChannel.connect(socketAddress).addListener((future: ChannelFuture) => browserRequestHandler.proxyConnectionComplete(future.getChannel, initialBrowserRequest))
  }
  override def exceptionCaught(context: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    browserRequestHandler.proxyException(e)
  }
  override def channelClosed(cxt: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    browserRequestHandler.proxyChannelClosed()
  }
}

@ChannelPipelineCoverage("one")
class ProxyResponseHandler(browserRequestHandler: BrowserRequestHandler) extends SimpleChannelUpstreamHandler {
  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    browserRequestHandler.proxyResponseReceived(e.getMessage.asInstanceOf[HttpResponse])
  }
}

/**
 * Gets an HttpRequest from the browser, sets up a connection to the
 * right server and hooks the two together.
 */
@ChannelPipelineCoverage("one")
class BrowserRequestHandler(id: Int, clientChannelFactory: ClientSocketChannelFactory) extends SimpleChannelUpstreamHandler {
  @volatile private var browserChannel: Channel = null
  @volatile private var proxyChannel: Channel = null

  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    browserChannel = e.getChannel

    val browserRequest = e.getMessage.asInstanceOf[HttpRequest]
    browserRequest.setHeader("Connection", browserRequest.getHeader("Proxy-Connection"))
    browserRequest.removeHeader("Proxy-Connection")
    val host = browserRequest.getHeader(HttpHeaders.Names.HOST) match {
      case "fakehttp" => "fakehttp"
      case s => s // "localhost"
    }

    log("Got request for "+browserRequest.getUri+" (host="+host+") (method="+browserRequest.getMethod+")")
    if (host == "fakehttp") {
      val responseBytes = (<html><body>
        {for (entry <- Traffic.hits) yield
          <p>{entry.getKey} = {entry.getValue}</p>
        }
      </body></html>).toString.getBytes("UTF-8")

      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
      response.setHeader("Server", "fakehttp")
      response.setHeader("Content-Length", responseBytes.length.toString)
      response.setContent(ChannelBuffers.wrappedBuffer(responseBytes))

      sendDownstream(browserChannel, response)
    } else if (proxyChannel != null) {
      Traffic.record(browserRequest.getUri)
      sendDownstream(proxyChannel, browserRequest)
    } else {
      Traffic.record(browserRequest.getUri)
      createProxyChannel(host, browserRequest)
    }
  }
  
  def proxyConnectionComplete(proxyChannel: Channel, initialBrowserRequest: HttpRequest): Unit = {
    if (!proxyChannel.isConnected) {
      log("Proxy connection failed "+proxyChannel)
      safelyCloseChannels
    } else {
      sendDownstream(proxyChannel, initialBrowserRequest)
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
    safelyCloseChannels
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
    browserChannel = null
    safelyCloseChannels
  }

  private def createProxyChannel(host: String, initialBrowserRequest: HttpRequest): Unit = {
    val proxyPipeline = Channels.pipeline()
    proxyPipeline.addFirst("connector", new ProxyConnectorHandler(this, new InetSocketAddress(host, 80), initialBrowserRequest))
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
    ioDone.addListener((future: ChannelFuture) => if (!future.isSuccess) { log("I/O error sending to "+channel) ; safelyCloseChannels })
    channel.getPipeline.sendDownstream(new DownstreamMessageEvent(channel, ioDone, message, null))
  }

  private def log(message: String): Unit = {
    println(id+" - "+message)
  }

  private def safelyCloseChannels(): Unit = {
    val b = browserChannel
    val p = proxyChannel
    browserChannel = null
    proxyChannel = null
    if (b != null && b.isConnected) b.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    if (p != null && p.isConnected) p.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
  }
}

object Implicits {
  implicit def blockToListener(block: ChannelFuture => Unit): ChannelFutureListener = new ChannelFutureListener() {
    override def operationComplete(future: ChannelFuture) = block(future)
  }
}
