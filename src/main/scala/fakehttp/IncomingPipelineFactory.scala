package fakehttp

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.channel.socket.ClientSocketChannelFactory
import fakehttp.ssl.SslMode
import fakehttp.interceptor.Interceptor

class IncomingPipelineFactory(
  interceptor: Interceptor,
  sslMode: SslMode,
  outgoingChannelFactory: ClientSocketChannelFactory)
  extends ChannelPipelineFactory {

  private val id = new AtomicInteger
  private val openHandlers = new ConcurrentHashMap[IncomingRequestHandler, Int]()

  def getPipeline(): ChannelPipeline = {
    val incomingRequestHandler = new IncomingRequestHandler(id.getAndIncrement, interceptor, sslMode, this, outgoingChannelFactory)

    // Remember this so we can close it on shut down
    openHandlers.put(incomingRequestHandler, 0)

    val pipeline = Channels.pipeline()
    pipeline.addLast("decoder", new HttpRequestDecoder(4096 * 4, 8192, 8192))
    pipeline.addLast("encoder", new HttpResponseEncoder())
    pipeline.addLast("handler", incomingRequestHandler)
    return pipeline
  }

  def closeRequestHandlers(): Unit = {
    val i = openHandlers.keySet.iterator
    while (i.hasNext) i.next.safelyCloseChannels
  }

  def handlerClosed(incomingRequestHandler: IncomingRequestHandler): Unit = {
    openHandlers.remove(incomingRequestHandler)
  }
}
