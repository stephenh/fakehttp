package fakehttp

import org.jboss.netty.handler.codec.http._
import scala.xml.NodeSeq

object FakeHttpHandler {
  def handle(httpRequest: HttpRequest): NodeSeq = {
    <html><body>
      {for (entry <- Traffic.hits) yield
        <p>{entry.getKey} = {entry.getValue}</p>
      }
    </body></html>
  }
}
