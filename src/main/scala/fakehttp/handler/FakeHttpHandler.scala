package fakehttp.handler

import org.jboss.netty.buffer._
import org.jboss.netty.handler.codec.http._

class FakeHttpHandler extends HttpHandler {
  def handle(req: HttpRequest): HandleResult = {
    val host = req.getHeader(HttpHeaders.Names.HOST) match {
      case "fakehttp" => "fakehttp"
      case other => "localhost"
    }
    val port = req.getHeader(HttpHeaders.Names.HOST) match {
      case "fakehttp" => 443
      case p => 80
    }

    Traffic.record(req.getUri)

    val html = <html><body>
      {for (entry <- Traffic.hits) yield
        <p>{entry.getKey} = {entry.getValue}</p>
      }
    </body></html>.toString.getBytes("UTF-8")
    
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Server", "fakehttp")
    response.setHeader("Content-Length", html.length.toString)
    response.setContent(ChannelBuffers.wrappedBuffer(html))
    return StaticResult(response)
  }

}
