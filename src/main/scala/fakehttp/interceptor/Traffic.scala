package fakehttp.interceptor

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConversions._

/** Keeps hit stats if using {@link LocalhostInterceptor}. */
object Traffic {
  private val map = new ConcurrentHashMap[String, AtomicInteger]()

  def record(uri: String): Unit = {
    var i = map.putIfAbsent(uri, new AtomicInteger)
    if (i == null) i = map.get(uri)
    i.incrementAndGet
  }

  def hits = asScalaSet(map.entrySet)
}
