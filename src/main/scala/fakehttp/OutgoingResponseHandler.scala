package fakehttp

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._

/** Passes responses from the destination server back to the {@link IncomingRequestHandler} for processing. */
@ChannelPipelineCoverage("one")
class OutgoingResponseHandler(incomingRequestHandler: IncomingRequestHandler) extends SimpleChannelUpstreamHandler {
  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    incomingRequestHandler.outgoingResponseReceived(e.getMessage)
  }
}
