package example

/**
 * Simple calculator to demonstrate coverage filtering.
 * The case class boilerplate methods (copy, productElement, etc.)
 * will be filtered from coverage reports.
 */
case class Calculator(precision: Int = 2) {
  def add(a: Double, b: Double): Double = a + b
  
  def subtract(a: Double, b: Double): Double = a - b
  
  def multiply(a: Double, b: Double): Double = a * b
  
  def divide(a: Double, b: Double): Double = {
    if (b == 0) throw new ArithmeticException("Division by zero")
    a / b
  }
}
