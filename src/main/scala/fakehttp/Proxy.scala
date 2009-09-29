package fakehttp

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory

object Proxy {
  def main(args: Array[String]): Unit = {
    val port = args(0).toInt
    val bossThreadPool = Executors.newCachedThreadPool()
    val workerThreadPool = Executors.newCachedThreadPool()

    val clientChannelFactory = new NioClientSocketChannelFactory(bossThreadPool, workerThreadPool)

    val serverBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(bossThreadPool, workerThreadPool))
    val serverPipelineFactory = new ServerPipelineFactory(clientChannelFactory)
    serverBootstrap.setPipelineFactory(serverPipelineFactory)
    val serverChannel = serverBootstrap.bind(new InetSocketAddress(port));

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        serverChannel.close.awaitUninterruptibly
        serverPipelineFactory.closeBrowserRequestHandlers
        bossThreadPool.shutdown
        workerThreadPool.shutdown
        bossThreadPool.awaitTermination(1, TimeUnit.MINUTES)
        workerThreadPool.awaitTermination(1, TimeUnit.MINUTES)
      }
    })
    // Use to get Eclipse to call the shutdown hook
    // System.in.read ; System.exit(0)
  }
}
