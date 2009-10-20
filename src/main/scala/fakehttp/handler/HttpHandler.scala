package fakehttp.handler

import org.jboss.netty.handler.codec.http._

sealed abstract class HandleResult
case class ProxyResult(host: String, port: Int) extends HandleResult
case class StaticResult(response: HttpResponse) extends HandleResult

trait HttpHandler {
  def handle(request: HttpRequest): HandleResult
}
