package fakehttp

import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger
import org.jboss.netty.channel.Channels
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.channel.socket.ClientSocketChannelFactory

class ServerPipelineFactory(clientChannelFactory: ClientSocketChannelFactory) extends ChannelPipelineFactory {
  val id = new AtomicInteger
  val openBrowserHandlers = new ConcurrentSkipListSet[ServerBrowserRequestHandler]()
  def getPipeline(): ChannelPipeline = {
    val browserRequestHandler = new ServerBrowserRequestHandler(this, id.getAndIncrement, clientChannelFactory)
    openBrowserHandlers.add(browserRequestHandler)

    val pipeline = Channels.pipeline()
    pipeline.addLast("decoder", new HttpRequestDecoder(4096 * 4, 8192, 8192))
    pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    pipeline.addLast("encoder", new HttpResponseEncoder())
    pipeline.addLast("handler", browserRequestHandler)
    return pipeline
  }
  def closeBrowserRequestHandlers(): Unit = {
    val i = openBrowserHandlers.iterator
    while (i.hasNext) i.next.safelyCloseChannels
  }
  def browserRequestHanderClosed(browserRequestHandler: ServerBrowserRequestHandler): Unit = {
    openBrowserHandlers.remove(browserRequestHandler)
  }
}
