package fakehttp.interceptor

import org.jboss.netty.handler.codec.http._

/**
 * This trait adds header rewriting to the interceptor.
 * 
 * To use, define a header with prefix "X-Fakehttp-Header-Rewrite-{header}".  The value of this header will be written
 * into the "{header}" header.  These X-Fakehttp-Header-Rewrite-* headers will then be removed.
 * 
 * @author darren
 *
 */
trait HeaderRewriting extends Interceptor {
  
  import HeaderRewriting._
  import scala.collection.JavaConversions._
  
  override abstract def intercept(request: HttpRequest): InterceptResult = {
    request.getHeaderNames filter { _.startsWith(PREFIX) } foreach { header =>
      val value = request.getHeader(header)
      request.setHeader(header.substring(PREFIX_LENGTH), value)
      System.err.println("Setting header [%s] to [%s]".format(header.substring(PREFIX_LENGTH), value))
      request.removeHeader(header)
    }
    super.intercept(request)
  }
  
}

object HeaderRewriting {
  val PREFIX = "X-Fakehttp-Header-Rewrite-"
  val PREFIX_LENGTH = PREFIX.length
}
