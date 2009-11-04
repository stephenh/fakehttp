package fakehttp

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.bootstrap.ServerBootstrap
import fakehttp.interceptor._
import fakehttp.ssl._

/**
 * Configures and starts netty running the fakehttp proxy.
 *
 * Also handles cleanly shutdowning any existing connections.
 */
object Proxy {
  def main(args: Array[String]): Unit = {
    val port = args(0).toInt
    val pool = Executors.newCachedThreadPool()
    val interceptor = new NoopInterceptor()
    val sslMode = new OpaqueSslMode() // ClearSslMode()

    // The IncomingRequestHandler will create outgoing traffic, so pass along the outgoingChannelFactory
    val outgoingChannelFactory = new NioClientSocketChannelFactory(pool, pool)
    val incomingPipelineFactory = new IncomingPipelineFactory(interceptor, sslMode, outgoingChannelFactory)

    // Bootstrap and start the proxy listening for incoming connections
    val incomingBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(pool, pool))
    incomingBootstrap.setPipelineFactory(incomingPipelineFactory)
    val incomingChannel = incomingBootstrap.bind(new InetSocketAddress(port));

    System.err.println("Proxy started on "+port)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        incomingChannel.close.awaitUninterruptibly
        incomingPipelineFactory.closeRequestHandlers
        pool.shutdown
        pool.awaitTermination(1, TimeUnit.MINUTES)
        System.err.println("Proxy shutdown")
      }
    })

    // Use to get Eclipse to call the shutdown hook
    System.in.read ; System.exit(0)
  }
}
