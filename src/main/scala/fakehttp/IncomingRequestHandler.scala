package fakehttp

import java.net.InetSocketAddress
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.ClientSocketChannelFactory
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.ssl.SslHandler
import fakehttp.Implicits._
import fakehttp.interceptor._
import fakehttp.ssl._

/**
 * Gets an HttpRequest from the browser, sets up a connection to the
 * right server and hooks the two together.
 */
@ChannelPipelineCoverage("one")
class IncomingRequestHandler(
  id: Int,
  interceptor: Interceptor,
  sslMode: SslMode,
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
    if (forwardRawMessage(e.getMessage)) {
      log("Got "+e.getMessage+" for "+lastHost)
      sendDownstream(outgoingChannel, e.getMessage)
      return
    }

    val req = e.getMessage.asInstanceOf[HttpRequest]
    log("Got "+req.getMethod+" request for "+req.getUri)
    interceptor.intercept(req) match {
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
      sslMode.setupIncomingForSsl(this, incomingChannel, lastHost)
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
    lastHost = host

    val outgoingPipeline = Channels.pipeline()
    if (initialBrowserRequest.getMethod() == HttpMethod.CONNECT) {
      sslMode.setupOutgoingForSsl(this, outgoingPipeline)
    }

    outgoingPipeline.addFirst("connector", new OutgoingConnectHandler(this, new InetSocketAddress(host, port), initialBrowserRequest))
    // Put this back if you want to translate the response
    // outgoingPipeline.addLast("decoder", new HttpResponseDecoder())
    outgoingPipeline.addLast("encoder", new HttpRequestEncoder())
    outgoingPipeline.addLast("outgoingResponse", new OutgoingResponseHandler(this))
    outgoingChannelFactory.newChannel(outgoingPipeline)
  }

  private def sendDownstream(channel: Channel, message: Object): Unit = {
    sendDownstream(channel, message, null)
  }

  def sendDownstream(channel: Channel, message: Object, onSuccess: ChannelFuture => Unit): Unit = {
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

  /** @return whether we should skip introspecting the message and just forward it */
  private def forwardRawMessage(message: Object): Boolean = {
    // If ChannelBuffer, our HttpMessageDecoder was removed by OpaqueSslMode
    // If HttpChunk, forward onto the existing connection
    return message.isInstanceOf[ChannelBuffer] || message.isInstanceOf[HttpChunk]
  }

  private def log(message: String): Unit = {
    System.err.println(id+" - "+message)
  }
}
