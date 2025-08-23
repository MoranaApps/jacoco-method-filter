package io.moranaapps.jacocomethodfilter

import java.util.regex.Pattern

object Glob {
  /** Minimal glob -> regex conversion: supports '*' and '?' only. Escapes regex metachars. */
  def toRegex(glob: String): Pattern = {
    val sb = new StringBuilder("^")
    glob.foreach {
      case '*' => sb.append(".*")
      case '?' => sb.append(".")
      case c if "\\.^$+{}[]()|".contains(c) => sb.append("\\").append(c)
      case c => sb.append(c)
    }
    sb.append("$")
    Pattern.compile(sb.toString)
  }
}
