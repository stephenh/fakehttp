package fakehttp.handler

import org.jboss.netty.handler.codec.http._

class NoopHandler extends HttpHandler {
  def handle(req: HttpRequest): HandleResult = {
    val host = req.getHeader(HttpHeaders.Names.HOST)
    val port = if (req.getMethod == HttpMethod.CONNECT) {
      req.getUri.split(":").last.toInt
    } else {
      80
    }
    return ProxyResult(host, port)
  }
}