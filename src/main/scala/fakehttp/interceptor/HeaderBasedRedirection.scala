package fakehttp.interceptor

import org.jboss.netty.handler.codec.http._

/**
 * This trait adds support for specifying proxy redirection on a per-request basis depending the request headers.
 * 
 * Currently supported request headers:
 * 
 * X-Fakehttp-Remote-Host: specifies the remote host to send the request to
 * X-Fakehttp-Remote-Port: specifies the remote port to send the request to
 * 
 * Processed headers will be removed before the request is forwarded.
 */
trait HeaderBasedRedirection extends Interceptor {

  private val hostRegex = """^((?:(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*(?:[A-Za-z]|[A-Za-z][A-Za-z0-9\-]*[A-Za-z0-9]))$""".r
  private val portRegex = """([1-9]\d*)""".r
  
  override protected def parseHostAndPort(request: HttpRequest): Tuple2[String, Int] = {
    val (defaultHost, defaultPort) = super.parseHostAndPort(request)
    val host = request.getHeader("X-Fakehttp-Remote-Host") match {
      case hostRegex(host) => host
      case _ => defaultHost
    }
    val port = request.getHeader("X-Fakehttp-Remote-Port") match {
      case portRegex(port) => port.toInt
      case _ => defaultPort 
    }
    
    request.removeHeader("X-Fakehttp-Remote-Host")
    request.removeHeader("X-Fakehttp-Remote-Port")
    
    (host, port)
  }
 
}