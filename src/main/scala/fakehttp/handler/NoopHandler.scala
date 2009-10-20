package fakehttp.handler

import org.jboss.netty.handler.codec.http._

class NoopHandler extends HttpHandler {
  def handle(req: HttpRequest): HandleResult = {
    // Watch for foo.com:123
    val parts = req.getHeader(HttpHeaders.Names.HOST).split(":")
    val host = parts(0)
    val port = if (parts.size > 1) {
      parts(1).toInt
    } else if (req.getMethod == HttpMethod.CONNECT) {
      req.getUri.split(":").last.toInt
    } else {
      80
    }
    return ProxyResult(host, port)
  }
}