package fakehttp.interceptor

import org.jboss.netty.handler.codec.http._

/**
 * This trait adds support for specifying proxy redirection on a per-request basis depending the request headers.
 * 
 * Currently supported request headers:
 * 
 * X-Fakehttp-Remote-Host: specifies the remote host to send the request to
 * X-Fakehttp-Remote-Port: specifies the remote port to send the request to
 * X-Fakehttp-Applicable-Host: specifies the hostname for which to consider proxying on basis of
 *   X-Fakehttp-Remote-{Host,Port} (useful for 302 redirects where client UA may send subsequent requests w/
 *   same headers, even though host may have changed).
 *   (Optional: When not set, any X-Fakehttp-Remote-{Host,Port} considered applicable.)
 * 
 * Processed headers will be removed before the request is forwarded.
 */
trait HeaderBasedRedirection extends Interceptor {

  import HeaderBasedRedirection._

  override protected def parseHostAndPort(request: HttpRequest): Tuple2[String, Int] = {
    val (defaultHost, defaultPort) = super.parseHostAndPort(request)
    log("Default: (host: %s) (port: %d)".format(defaultHost, defaultPort))
    val applicableHost = Option(request.getHeader(applicableHostHeader)).flatMap { str => hostRegex.findFirstIn(str) }
    log("Applicable: (host: %s)".format(applicableHost.getOrElse("None")))

    val (host, port) = applicableHost match {
      case Some(applicableHost) if applicableHost != defaultHost =>
        (defaultHost, defaultPort)
      case _ =>
        val remoteHost = Option(request.getHeader(remoteHostHeader)).flatMap { str => hostRegex.findFirstIn(str) }
        val remotePort = Option(request.getHeader(remotePortHeader)).flatMap { str => portRegex.findFirstIn(str) } map { _.toInt }
        log("Remote: (host: %s) (port: %s)".format(remoteHost, remotePort))
        (remoteHost.getOrElse(defaultHost),
         remotePort.getOrElse(defaultPort))
    }
    
    request.removeHeader(remoteHostHeader)
    request.removeHeader(remotePortHeader)
    request.removeHeader(applicableHostHeader)

    log("Result: (host: %s) (port: %d)".format(host, port))
    (host, port)
  }

  private def log(message: String) {
    System.err.println(message)
  }
}

object HeaderBasedRedirection {

  final val remoteHostHeader     = "X-Fakehttp-Remote-Host"
  final val remotePortHeader     = "X-Fakehttp-Remote-Port"
  final val applicableHostHeader = "X-Fakehttp-Applicable-Host"

  private val hostRegex = """^((?:(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*(?:[A-Za-z]|[A-Za-z][A-Za-z0-9\-]*[A-Za-z0-9]))$""".r
  private val portRegex = """([1-9]\d*)""".r
}
