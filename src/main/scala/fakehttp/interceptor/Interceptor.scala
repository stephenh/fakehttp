package fakehttp.interceptor

import org.jboss.netty.handler.codec.http._

sealed abstract class InterceptResult
case class ProxyResult(host: String, port: Int) extends InterceptResult
case class StaticResult(response: HttpResponse) extends InterceptResult

trait Interceptor {
  def intercept(request: HttpRequest): InterceptResult

  protected def parseHostAndPort(req: HttpRequest): Tuple2[String, Int] = {
    val parts = req.getHeader(HttpHeaders.Names.HOST).split(":") // Watch for foo.com:123
    val host = parts(0)
    val port = if (parts.size > 1) {
      parts(1).toInt
    } else if (req.getMethod == HttpMethod.CONNECT) {
      req.getUri.split(":").last.toInt
    } else {
      80
    }
    return (host, port)
  }
}
