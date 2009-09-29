package fakehttp

import java.net.InetSocketAddress
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import fakehttp.Implicits._

@ChannelPipelineCoverage("one")
class ProxyConnectorHandler(
  browserRequestHandler: ServerBrowserRequestHandler,
  socketAddress: InetSocketAddress,
  initialBrowserRequest: HttpRequest)
  extends SimpleChannelUpstreamHandler {

  override def channelOpen(context: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    e.getChannel.connect(socketAddress).addListener((future: ChannelFuture) => {
      browserRequestHandler.proxyConnectionComplete(future.getChannel, initialBrowserRequest)
    })
  }

  override def exceptionCaught(context: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    browserRequestHandler.proxyException(e)
  }

  override def channelClosed(cxt: ChannelHandlerContext, e: ChannelStateEvent): Unit = {
    browserRequestHandler.proxyChannelClosed()
  }
}
