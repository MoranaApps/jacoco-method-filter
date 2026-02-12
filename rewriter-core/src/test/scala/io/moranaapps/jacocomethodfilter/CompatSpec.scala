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
}
