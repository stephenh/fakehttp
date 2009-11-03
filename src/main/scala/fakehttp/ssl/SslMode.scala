package fakehttp.ssl

import fakehttp._
import org.jboss.netty.channel._

trait SslMode {
  /** Called before making the outgoing destination connection. */
  def setupOutgoingForSsl(handler: IncomingRequestHandler, outgoing: ChannelPipeline): Unit

  /** Called after the outgoing destination connection has been made. */
  def setupIncomingForSsl(handler: IncomingRequestHandler, incoming: Channel, host: String): Unit
}
