package example

import org.scalatest.FunSuite

class CalculatorTest extends FunSuite {
  val calc = new Calculator
  
  test("addition") {
    assert(calc.add(2, 3) === 5)
  }
  
  test("subtraction") {
    assert(calc.subtract(5, 3) === 2)
  }
  
  test("multiplication") {
    assert(calc.multiply(3, 4) === 12)
  }
  
  test("division") {
    assert(calc.divide(12, 3) === 4)
  }
  
  test("division by zero throws exception") {
    intercept[IllegalArgumentException] {
      calc.divide(5, 0)
    }
  }
}
