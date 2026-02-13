package example

class Calculator {
  def add(a: Int, b: Int): Int = a + b
  
  def subtract(a: Int, b: Int): Int = a - b
  
  def multiply(a: Int, b: Int): Int = a * b
  
  def divide(a: Int, b: Int): Int = {
    require(b != 0, "Cannot divide by zero")
    a / b
  }
}
