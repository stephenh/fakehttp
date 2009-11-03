package fakehttp.ssl

import fakehttp._
import org.jboss.netty.buffer._
import org.jboss.netty.channel._

/**
 * After establishing a connection to the destination, gives up
 * on encoding/decoding the content has HttpMessage objects and
 * just passes raw bytes back and forth.
 */
class OpaqueSslMode extends SslMode {
  def setupOutgoingForSsl(handler: IncomingRequestHandler, outgoingPipeline: ChannelPipeline): Unit = {
  }

  def setupIncomingForSsl(handler: IncomingRequestHandler, incomingChannel: Channel, host: String): Unit = {
    val buffer = ChannelBuffers.wrappedBuffer("HTTP/1.1 200 Connection established\r\n\r\n".getBytes())
    handler.sendDownstream(incomingChannel, buffer, (future: ChannelFuture) => {
      incomingChannel.getPipeline.remove("decoder")
    })
  }
}
