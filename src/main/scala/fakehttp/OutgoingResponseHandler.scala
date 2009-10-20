package fakehttp

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

@ChannelPipelineCoverage("one")
class OutgoingResponseHandler(incomingRequestHandler: IncomingRequestHandler) extends SimpleChannelUpstreamHandler {
  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    incomingRequestHandler.outgoingResponseReceived(e.getMessage) //.asInstanceOf[HttpResponse])
  }
}
