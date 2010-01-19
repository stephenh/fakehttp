package fakehttp

import org.jboss.netty.channel._

object Implicits {
  /** Converts a Scala Function1[ChannelFuture, Unit] to a {@link ChannelFutureListener}. */
  implicit def blockToListener(block: ChannelFuture => Unit) = new ChannelFutureListener() {
    override def operationComplete(future: ChannelFuture) = block(future)
  }
}
