package fakehttp

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

@ChannelPipelineCoverage("one")
class ProxyResponseHandler(browserRequestHandler: ServerBrowserRequestHandler) extends SimpleChannelUpstreamHandler {
  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    browserRequestHandler.proxyResponseReceived(e.getMessage.asInstanceOf[HttpResponse])
  }
}
