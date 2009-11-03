package fakehttp.interceptor

import org.jboss.netty.handler.codec.http._

class NoopInterceptor extends Interceptor {
  def intercept(req: HttpRequest): InterceptResult = {
    val p = parseHostAndPort(req)
    return ProxyResult(p._1, p._2)
  }
}