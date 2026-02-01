package example

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CalculatorTest extends AnyFunSuite with Matchers {
  val calc = Calculator()

  test("add should sum two numbers") {
    calc.add(2, 3) shouldBe 5.0
  }

  test("subtract should find difference") {
    calc.subtract(5, 3) shouldBe 2.0
  }

  test("multiply should find product") {
    calc.multiply(4, 3) shouldBe 12.0
  }

  test("divide should find quotient") {
    calc.divide(10, 2) shouldBe 5.0
  }

  test("divide by zero should throw exception") {
    assertThrows[ArithmeticException] {
      calc.divide(10, 0)
    }
  }
}
