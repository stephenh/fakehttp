package fakehttp

import org.jboss.netty.channel._

object Implicits {
  implicit def blockToListener(block: ChannelFuture => Unit): ChannelFutureListener = new ChannelFutureListener() {
    override def operationComplete(future: ChannelFuture) = block(future)
  }
}
