package fakehttp

import java.net.InetSocketAddress
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import fakehttp.Implicits._

@ChannelPipelineCoverage("one")
class OutgoingConnectHandler(
  incomingRequestHandler: IncomingRequestHandler,
  socketAddress: InetSocketAddress,
  initialBrowserRequest: HttpRequest)
  extends SimpleChannelUpstreamHandler {

  override def channelOpen(context: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    e.getChannel.connect(socketAddress).addListener((future: ChannelFuture) => {
      incomingRequestHandler.outgoingConnectionComplete(future.getChannel, initialBrowserRequest)
    })
  }

  override def exceptionCaught(context: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    incomingRequestHandler.outgoingException(e)
  }

  override def channelClosed(cxt: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    incomingRequestHandler.outgoingChannelClosed()
  }
}
