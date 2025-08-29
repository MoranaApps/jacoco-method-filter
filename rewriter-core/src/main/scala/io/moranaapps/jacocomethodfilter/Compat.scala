package io.moranaapps.jacocomethodfilter

object Compat {
  /** Minimal Using for 2.12 */
  def using[A <: AutoCloseable, B](res: => A)(f: A => B): B = {
    val r = res
    try f(r) finally if (r != null) r.close()
  }
}
