package example

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StringFormatterTest extends AnyFunSuite with Matchers {
  val fmt = new StringFormatter("[", "]")

  test("format should wrap value with prefix and suffix") {
    fmt.format("hello") shouldBe "[hello]"
  }

  test("padLeft should pad shorter strings") {
    fmt.padLeft("42", 5) shouldBe "   42"
  }

  test("padLeft should not pad strings at width") {
    fmt.padLeft("hello", 5) shouldBe "hello"
  }

  test("padRight should pad shorter strings") {
    fmt.padRight("42", 5) shouldBe "42   "
  }

  test("truncate should shorten long strings") {
    fmt.truncate("abcdefgh", 5) shouldBe "abcde"
  }

  test("truncate should leave short strings unchanged") {
    fmt.truncate("abc", 5) shouldBe "abc"
  }

  test("repeat should duplicate string") {
    fmt.repeat("ab", 3) shouldBe "ababab"
  }
}
