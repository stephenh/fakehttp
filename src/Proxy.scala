
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.buffer._
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.codec.http._

object Proxy {
  def main(args: Array[String]): Unit = {
    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool()))
    bootstrap.setPipelineFactory(new HttpServerPipelineFactory());
    bootstrap.bind(new InetSocketAddress(8080));
  }
}

class HttpServerPipelineFactory extends ChannelPipelineFactory {
  def getPipeline(): ChannelPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("decoder", new HttpRequestDecoder())
    pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    pipeline.addLast("encoder", new HttpResponseEncoder())
    pipeline.addLast("handler", new HttpRequestHandler())
    return pipeline
  }
}


@ChannelPipelineCoverage("one")
class HttpRequestHandler extends SimpleChannelUpstreamHandler {
  override def messageReceived(cxt: ChannelHandlerContext, e: MessageEvent): Unit = {
    val responseContent = new StringBuilder()
    val request = e.getMessage.asInstanceOf[HttpRequest]

    responseContent.append("WELCOME TO THE WILD WILD WEB SERVER\r\n");
    responseContent.append("===================================\r\n");
    responseContent.append("VERSION: " + request.getProtocolVersion.getText + "\r\n");
    if (request.containsHeader(HttpHeaders.Names.HOST)) {
      responseContent.append("HOSTNAME: " + request.getHeader(HttpHeaders.Names.HOST) + "\r\n");
    }
    responseContent.append("REQUEST_URI: " + request.getUri + "\r\n\r\n");
    iterate(request.getHeaderNames) { name =>
      iterate(request.getHeaders(name)) { value =>
        responseContent.append("HEADER: " + name + " = " + value + "\r\n"); 
      }
    }
    iterate(new QueryStringDecoder(request.getUri).getParameters.entrySet) { entry =>
      iterate(entry.getValue) { value =>
        responseContent.append("PARAM: " + entry.getKey + " = " + value + "\r\n"); 
      }
    }
    
    val content = request.getContent;
    if (content.readable()) {
      responseContent.append("CONTENT: " + content.toString("UTF-8") + "\r\n");
    }
    writeResponse(request, responseContent, e);
  }

  def writeResponse(request: HttpRequest, responseContent: StringBuilder, e: MessageEvent): Unit = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8")

    val buf = ChannelBuffers.copiedBuffer(responseContent.toString(), "UTF-8")
    response.setContent(buf)
    response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf.readableBytes()))

    val cookieString = request.getHeader(HttpHeaders.Names.COOKIE)
    if (cookieString != null) {
      val cookies = new CookieDecoder().decode(cookieString);
      if (!cookies.isEmpty()) {
        val cookieEncoder = new CookieEncoder(true);
        val i = cookies.iterator
        while (i.hasNext) cookieEncoder.addCookie(i.next)
        response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode())
      }
    }

    // Write the response.
    val future = e.getChannel().write(response)

    // Close the connection after the write operation is done if necessary.
    if (shouldCloseConnection(request)) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent): Unit = {
    e.getCause().printStackTrace();
    e.getChannel().close();
  }
  
  private def shouldCloseConnection(request: HttpRequest): Boolean = {
    HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION)) ||
    (request.getProtocolVersion().equals(HttpVersion.HTTP_1_0) && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.getHeader(HttpHeaders.Names.CONNECTION)))
  }

  private def iterate[T](collection: java.util.Collection[T])(block: T => Unit): Unit = {
    val i = collection.iterator
    while (i.hasNext) {
      block.apply(i.next)
    }
  }
}
