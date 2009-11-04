package fakehttp.interceptor

import org.jboss.netty.handler.codec.http._

sealed abstract class InterceptResult
case class ProxyResult(host: String, port: Int) extends InterceptResult
case class StaticResult(response: HttpResponse) extends InterceptResult

/** Interface to allow mucking with incoming HttpRequests. */
trait Interceptor {
  def intercept(request: HttpRequest): InterceptResult

  /** @return the host and port parsed from the appropriate uri/headers */
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
