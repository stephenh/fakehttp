package fakehttp.ssl

import fakehttp._
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.handler.ssl.SslHandler

/**
 * Sets up as a man-in-the-middle between the client and the destination so
 * continues to decode HttpMessages.
 */
class ClearSslMode extends SslMode {
  def setupOutgoingForSsl(handler: IncomingRequestHandler, outgoingPipeline: ChannelPipeline): Unit = {
    val clientEngine = CyberVillainsContextFactory.clientContext.createSSLEngine
    clientEngine.setUseClientMode(true)
    outgoingPipeline.addLast("ssl", new SslHandler(clientEngine))
  }

  def setupIncomingForSsl(handler: IncomingRequestHandler, incomingChannel: Channel, host: String): Unit = {
    val serverEngine = CyberVillainsContextFactory.createServerContextForHost(host).createSSLEngine
    serverEngine.setUseClientMode(false)
    val buffer = ChannelBuffers.wrappedBuffer("HTTP/1.1 200 Connection established\r\n\r\n".getBytes())
    handler.sendDownstream(incomingChannel, buffer, (future: ChannelFuture) => {
      incomingChannel.getPipeline.addFirst("ssl", new SslHandler(serverEngine))
    })
  }
}
