package fakehttp.interceptor

import org.jboss.netty.buffer._
import org.jboss.netty.handler.codec.http._

class LocalhostInterceptor extends Interceptor {
  def intercept(req: HttpRequest): InterceptResult = {
    return parseHostAndPort(req) match {
      case ("fakehttp", p) => StaticResult(reportHits())
      case (other, p) => Traffic.record(req.getUri) ; ProxyResult("localhost", p)
    }
  }

  private def reportHits(): HttpResponse = {
    val html = <html><body>
      {for (entry <- Traffic.hits) yield
        <p>{entry.getKey} = {entry.getValue}</p>
      }
    </body></html>.toString.getBytes("UTF-8")

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Server", "fakehttp")
    response.setHeader("Content-Length", html.length.toString)
    response.setContent(ChannelBuffers.wrappedBuffer(html))
    response
  }

}
