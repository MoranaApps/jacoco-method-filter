package example

/**
 * Pure business-logic class with no boilerplate methods.
 * None of these methods should be matched by JaCoCo method filter rules,
 * so this class should NOT appear in verify output.
 */
class StringFormatter(prefix: String, suffix: String) {

  def format(value: String): String = s"$prefix$value$suffix"

  def padLeft(value: String, width: Int, padChar: Char = ' '): String = {
    if (value.length >= width) value
    else padChar.toString * (width - value.length) + value
  }

  def padRight(value: String, width: Int, padChar: Char = ' '): String = {
    if (value.length >= width) value
    else value + padChar.toString * (width - value.length)
  }

  def truncate(value: String, maxLength: Int): String = {
    if (value.length <= maxLength) value
    else value.substring(0, maxLength)
  }

  def repeat(value: String, times: Int): String = value * times
}
