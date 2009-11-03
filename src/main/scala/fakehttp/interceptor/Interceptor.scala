package fakehttp.interceptor

import org.jboss.netty.handler.codec.http._

sealed abstract class InterceptResult
case class ProxyResult(host: String, port: Int) extends InterceptResult
case class StaticResult(response: HttpResponse) extends InterceptResult

trait Interceptor {
  def intercept(request: HttpRequest): InterceptResult
}
