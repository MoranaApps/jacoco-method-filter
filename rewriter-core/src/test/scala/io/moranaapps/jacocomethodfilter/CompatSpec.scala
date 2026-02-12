package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite

class CompatSpec extends AnyFunSuite {

  test("using closes resource on success") {
    var closed = false
    val res = new AutoCloseable { def close(): Unit = closed = true }
    val result = Compat.using(res)(r => 42)
    assert(result == 42)
    assert(closed, "resource should be closed after success")
  }

  test("using closes resource on exception") {
    var closed = false
    val res = new AutoCloseable { def close(): Unit = closed = true }
    val ex = intercept[RuntimeException] {
      Compat.using(res)(_ => throw new RuntimeException("boom"))
    }
    assert(ex.getMessage == "boom")
    assert(closed, "resource should be closed after exception")
  }

  test("using propagates exception from resource creation") {
    intercept[RuntimeException] {
      Compat.using[AutoCloseable, Int](throw new RuntimeException("creation failed"))(_ => 1)
    }
  }

  test("RichJavaList.asScala converts java.util.List to mutable.Buffer") {
    import Compat._
    val jl = new java.util.ArrayList[String]()
    jl.add("a")
    jl.add("b")
    jl.add("c")
    val buf = jl.asScala
    assert(buf.size == 3)
    assert(buf(0) == "a")
    assert(buf(1) == "b")
    assert(buf(2) == "c")
  }

  test("RichJavaList.asScala handles empty list") {
    import Compat._
    val jl = new java.util.ArrayList[Int]()
    val buf = jl.asScala
    assert(buf.isEmpty)
  }

  test("RichJavaIterator.asScala converts java.util.Iterator to scala Iterator") {
    import Compat._
    val jl = new java.util.ArrayList[String]()
    jl.add("x")
    jl.add("y")
    val it = jl.iterator().asScala
    assert(it.hasNext)
    assert(it.next() == "x")
    assert(it.hasNext)
    assert(it.next() == "y")
    assert(!it.hasNext)
  }

  test("RichJavaIterator.asScala handles empty iterator") {
    import Compat._
    val jl = new java.util.ArrayList[String]()
    val it = jl.iterator().asScala
    assert(!it.hasNext)
  }
}
