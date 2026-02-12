package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite
import TestSupport._

class RulesLoadSpec extends AnyFunSuite {

  test("parses glob selectors + flags + predicates") {
    val file = write(tmpFile(), Seq(
      "# comment",
      "",
      "com.example.*#copy(*) public synthetic name-contains:opy id:copy1",
      "a.b.C#foo(I)I protected,bridge name-starts:fo name-ends:o",
      // ret:<glob> only matches return part
      "x.y.Z#bar(*) public ret:V"
    ))

    val rules = Rules.load(file)
    assert(rules.size == 3)

    val r1 = rules(0)
    assert(r1.flags == Set("public", "synthetic"))
    assert(r1.nameContains.contains("opy"))
    assert(r1.id.contains("copy1"))
    assert(r1.method.matcher("copy").matches())
    assert(r1.desc.matcher("(I)V").matches()) // "(*)" matches any desc

    val r2 = rules(1)
    assert(r2.flags == Set("protected", "bridge"))
    assert(r2.nameStarts.contains("fo"))
    assert(r2.nameEnds.contains("o"))
    assert(r2.method.matcher("foo").matches())
    assert(r2.desc.matcher("(I)I").matches())
    assert(!r2.desc.matcher("(I)V").matches())

    val r3 = rules(2)
    assert(r3.flags == Set("public"))
    assert(r3.retGlob.exists(_.matcher("V").matches()))
    assert(!r3.retGlob.exists(_.matcher("I").matches()))
  }

  test("glob selectors for class/method/desc (no regex)") {
    val file = write(tmpFile(), Seq(
      "*.ex*.*#do*(Ljava/lang/String;I)V static"
    ))
    val rules = Rules.load(file)
    val r = rules.head
    assert(r.flags == Set("static"))

    // Prefer testing through Rules.matches (covers dot/slash logic)
    val acc = access(staticA = true)

    // class + method + descriptor (positive)
    assert(Rules.matches(r, "com.example.service.User", "doWork", "(Ljava/lang/String;I)V", acc))
    assert(Rules.matches(r, "com.extlib.x",            "doWork", "(Ljava/lang/String;I)V", acc))

    // class negative
    assert(!Rules.matches(r, "org.other.X",            "doWork", "(Ljava/lang/String;I)V", acc))

    // method negative
    assert(!Rules.matches(r, "com.example.service.User", "work", "(Ljava/lang/String;I)V", acc))

    // descriptor negative
    assert(!Rules.matches(r, "com.example.service.User", "doWork", "(I)V", acc))

    // If you want to assert the raw pattern: use dot form
    assert(r.cls.matcher("com.extlib.x").matches())
    assert(!r.cls.matcher("org.other.X").matches())
  }

  test("descriptor normalization: \"\", \"()\", \"(*)\" → treated as wildcards") {
    val file = write(tmpFile(), Seq(
      "x.A#m1()",
      "x.A#m2",
      "x.A#m3(*)"
    ))
    val Seq(r1, r2, r3) = Rules.load(file)

    val anyDesc = Seq("(I)V", "()V", "(Ljava/lang/String;I)I")
    anyDesc.foreach { d =>
      assert(r1.desc.matcher(d).matches(), s"m1 should match $d")
      assert(r2.desc.matcher(d).matches(), s"m2 should match $d")
      assert(r3.desc.matcher(d).matches(), s"m3 should match $d")
    }
  }

  test("+ prefix parses as Include mode") {
    val file = write(tmpFile(), Seq(
      "com.example.*#copy(*) id:copy-excl",
      "+com.example.Config$#copy(*) id:copy-incl"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 2)
    
    val r1 = rules(0)
    assert(r1.mode == Exclude)
    assert(r1.id.contains("copy-excl"))
    
    val r2 = rules(1)
    assert(r2.mode == Include)
    assert(r2.id.contains("copy-incl"))
  }

  test("+ prefix works with all syntax forms") {
    val file = write(tmpFile(), Seq(
      "+com.example.*#foo public synthetic id:inc1",
      "+a.b.C#bar(I)I protected,bridge name-starts:ba id:inc2",
      "+x.y.Z#baz(*) ret:V id:inc3"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 3)
    
    rules.foreach { r =>
      assert(r.mode == Include)
    }
    
    // Verify flags and predicates still work
    assert(rules(0).flags == Set("public", "synthetic"))
    assert(rules(1).flags == Set("protected", "bridge"))
    assert(rules(1).nameStarts.contains("ba"))
    assert(rules(2).retGlob.exists(_.matcher("V").matches()))
  }

  test("unprefixed rules default to Exclude mode") {
    val file = write(tmpFile(), Seq(
      "com.example.*#copy(*)",
      "a.b.C#foo"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 2)
    
    rules.foreach { r =>
      assert(r.mode == Exclude)
    }
  }

  // --- Failure cases (leaf throws) ---

  test("load throws NoSuchFileException on missing file") {
    val missing = java.nio.file.Paths.get("/tmp/does-not-exist-rules-" + System.nanoTime() + ".txt")
    intercept[java.nio.file.NoSuchFileException] {
      Rules.load(missing)
    }
  }

  test("loadAll throws NoSuchFileException when local rules file is missing") {
    val missing = java.nio.file.Paths.get("/tmp/does-not-exist-local-" + System.nanoTime() + ".txt")
    intercept[java.nio.file.NoSuchFileException] {
      Rules.loadAll(None, Some(missing))
    }
  }

  test("loadAll throws NoSuchFileException when global rules path is missing") {
    val missing = "/tmp/does-not-exist-global-" + System.nanoTime() + ".txt"
    intercept[java.nio.file.NoSuchFileException] {
      Rules.loadAll(Some(missing), None)
    }
  }

  test("parseLine throws on missing method separator") {
    val ex = intercept[IllegalArgumentException] {
      Rules.parseLine("com.example.Foo")
    }
    assert(ex.getMessage.contains("Missing '#' separator"))
  }

  test("loadAll succeeds with valid files") {
    val globalFile = write(tmpFile("global-"), Seq("com.example.*#foo(*)"))
    val localFile = write(tmpFile("local-"), Seq("com.example.*#bar(*)"))
    val rules = Rules.loadAll(Some(globalFile.toString), Some(localFile))
    assert(rules.size == 2)
  }
}
