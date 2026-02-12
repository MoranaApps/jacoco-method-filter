package io.moranaapps.jacocomethodfilter

object Compat {
  /** Minimal Using for 2.12 */
  def using[A <: AutoCloseable, B](res: => A)(f: A => B): B = {
    val r = res
    try f(r) finally if (r != null) r.close()
  }

  // --- Collection converters (works on 2.11, 2.12, 2.13) -------------------
  // Avoids deprecated scala.collection.JavaConverters (2.13) and the
  // 2.13-only scala.jdk.CollectionConverters.

  import scala.collection.mutable

  implicit class RichJavaList[A](private val jl: java.util.List[A]) extends AnyVal {
    def asScala: mutable.Buffer[A] = {
      val buf = mutable.ArrayBuffer.empty[A]
      val it  = jl.iterator()
      while (it.hasNext) buf += it.next()
      buf
    }
  }

  implicit class RichJavaIterator[A](private val ji: java.util.Iterator[A]) extends AnyVal {
    def asScala: Iterator[A] = new Iterator[A] {
      def hasNext: Boolean = ji.hasNext
      def next(): A        = ji.next()
    }
  }
}
