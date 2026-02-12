package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite

class GlobSpec extends AnyFunSuite {

  private def matches(glob: String, input: String): Boolean =
    Glob.toRegex(glob).matcher(input).matches()

  test("literal string matches exactly") {
    assert(matches("hello", "hello"))
    assert(!matches("hello", "Hello"))
    assert(!matches("hello", "hello!"))
    assert(!matches("hello", ""))
  }

  test("* matches zero or more characters") {
    assert(matches("*", ""))
    assert(matches("*", "anything"))
    assert(matches("foo*", "foo"))
    assert(matches("foo*", "foobar"))
    assert(matches("*bar", "bar"))
    assert(matches("*bar", "foobar"))
    assert(matches("foo*bar", "foobar"))
    assert(matches("foo*bar", "foo123bar"))
    assert(!matches("foo*bar", "foo123baz"))
  }

  test("? matches exactly one character") {
    assert(matches("?", "a"))
    assert(!matches("?", ""))
    assert(!matches("?", "ab"))
    assert(matches("f?o", "foo"))
    assert(matches("f?o", "fXo"))
    assert(!matches("f?o", "fo"))
    assert(!matches("f?o", "fXXo"))
  }

  test("regex metacharacters are escaped") {
    // These chars should be treated as literals, not regex operators
    assert(matches("a.b", "a.b"))
    assert(!matches("a.b", "aXb"))

    assert(matches("a^b", "a^b"))
    assert(matches("a$b", "a$b"))
    assert(matches("a+b", "a+b"))
    assert(!matches("a+b", "aab"))

    assert(matches("a{b}", "a{b}"))
    assert(matches("a[b]", "a[b]"))
    assert(matches("a(b)", "a(b)"))
    assert(matches("a|b", "a|b"))
    assert(!matches("a|b", "a"))
    assert(!matches("a|b", "b"))

    assert(matches("a\\b", "a\\b"))
  }

  test("combined * and ? patterns") {
    assert(matches("*.class", "Foo.class"))
    assert(matches("*.class", ".class"))
    assert(!matches("*.class", "Foo.java"))
    assert(matches("?oo*", "foobar"))
    assert(matches("?oo*", "foo"))
    assert(!matches("?oo*", "oo"))
  }

  test("$ in glob is literal (Scala companion objects)") {
    assert(matches("com.example.*$", "com.example.User$"))
    assert(!matches("com.example.*$", "com.example.User"))
  }

  test("empty glob matches only empty string") {
    assert(matches("", ""))
    assert(!matches("", "x"))
  }
}
