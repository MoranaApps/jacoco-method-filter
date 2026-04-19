package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite
import org.objectweb.asm._
import java.nio.file.{Files, Path}
import TestSupport._

class VerifyScannerSpec extends AnyFunSuite {

  // Helper to create a simple class file in a temp directory
  private def createTestClass(dir: Path, className: String, methods: Seq[(String, String, Int)]): Unit = {
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS)
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className.replace('.', '/'), null, "java/lang/Object", null)
    
    // Add constructor
    val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    mv.visitCode()
    mv.visitVarInsn(Opcodes.ALOAD, 0)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    mv.visitInsn(Opcodes.RETURN)
    mv.visitMaxs(1, 1)
    mv.visitEnd()
    
    // Add requested methods
    methods.foreach { case (name, desc, access) =>
      val m = cw.visitMethod(access, name, desc, null, null)
      m.visitCode()
      
      // Generate correct return opcode based on descriptor's return type
      val returnTypeDesc = desc.substring(desc.lastIndexOf(')') + 1)
      returnTypeDesc.charAt(0) match {
        case 'V' =>
          m.visitInsn(Opcodes.RETURN)
        case 'L' | '[' =>
          m.visitInsn(Opcodes.ACONST_NULL)
          m.visitInsn(Opcodes.ARETURN)
        case 'Z' | 'B' | 'C' | 'S' | 'I' =>
          m.visitInsn(Opcodes.ICONST_0)
          m.visitInsn(Opcodes.IRETURN)
        case 'J' =>
          m.visitInsn(Opcodes.LCONST_0)
          m.visitInsn(Opcodes.LRETURN)
        case 'F' =>
          m.visitInsn(Opcodes.FCONST_0)
          m.visitInsn(Opcodes.FRETURN)
        case 'D' =>
          m.visitInsn(Opcodes.DCONST_0)
          m.visitInsn(Opcodes.DRETURN)
        case _ =>
          // Fallback: treat as reference type
          m.visitInsn(Opcodes.ACONST_NULL)
          m.visitInsn(Opcodes.ARETURN)
      }
      // ClassWriter.COMPUTE_MAXS will recalculate correct maxStack/maxLocals
      // (e.g., 2 for long/double which occupy 2 stack slots)
      m.visitMaxs(0, 0)
      m.visitEnd()
    }
    
    cw.visitEnd()
    
    val classFile = dir.resolve(className.replace('.', '/') + ".class")
    Files.createDirectories(classFile.getParent)
    Files.write(classFile, cw.toByteArray)
  }

  test("scan returns correct count of scanned classes") {
    val dir = Files.createTempDirectory("verify-test-")
    try {
      createTestClass(dir, "test.ClassA", Seq(("foo", "()V", Opcodes.ACC_PUBLIC)))
      createTestClass(dir, "test.ClassB", Seq(("bar", "()V", Opcodes.ACC_PUBLIC)))
      
      val rules = Seq.empty[MethodRule]
      val result = VerifyScanner.scan(dir, rules)
      
      assert(result.classesScanned == 2)
      assert(result.totalMatched == 0)
      assert(result.matches.isEmpty)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan matches methods against exclusion rules") {
    val dir = Files.createTempDirectory("verify-test-")
    try {
      createTestClass(dir, "test.Example", Seq(
        ("copy", "(I)Ltest/Example;", Opcodes.ACC_PUBLIC),
        ("equals", "(Ljava/lang/Object;)Z", Opcodes.ACC_PUBLIC)
      ))
      
      // Rule that matches "copy" method
      val rulesFile = tmpFile()
      write(rulesFile, Seq("test.Example#copy(*) id:case-copy"))
      val rules = Rules.load(rulesFile)
      
      val result = VerifyScanner.scan(dir, rules)
      
      assert(result.classesScanned == 1)
      assert(result.totalMatched == 1)
      assert(result.matches.size == 1)
      assert(result.matches.head.methodName == "copy")
      assert(result.matches.head.fqcn == "test.Example")
      assert(result.matches.head.outcome == Excluded)
      assert(result.matches.head.exclusionIds.contains("case-copy"))
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan records rescued methods when both exclusion and inclusion rules match") {
    val dir = Files.createTempDirectory("verify-test-")
    try {
      createTestClass(dir, "test.Config$", Seq(
        ("apply", "(Ljava/lang/Object;)Ltest/Config;", Opcodes.ACC_PUBLIC)
      ))
      
      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "test.*$#apply(*) id:comp-apply",
        "+test.Config$#apply(*) id:keep-config-apply"
      ))
      val rules = Rules.load(rulesFile)
      
      val result = VerifyScanner.scan(dir, rules)
      
      assert(result.classesScanned == 1)
      assert(result.totalMatched == 1)
      assert(result.matches.size == 1)
      assert(result.matches.head.outcome == Rescued)
      assert(result.matches.head.exclusionIds.contains("comp-apply"))
      assert(result.matches.head.inclusionIds.contains("keep-config-apply"))
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan excludes methods matched only by exclusion rules") {
    val dir = Files.createTempDirectory("verify-test-")
    try {
      createTestClass(dir, "test.Foo", Seq(
        ("apply", "(Ljava/lang/Object;)Ljava/lang/Object;", Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)
      ))
      
      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "test.*#apply(*) synthetic id:synthetic-methods",
        "test.*#apply(*) bridge id:bridge-methods"
      ))
      val rules = Rules.load(rulesFile)
      
      val result = VerifyScanner.scan(dir, rules)
      
      assert(result.classesScanned == 1)
      assert(result.totalMatched == 1)
      assert(result.matches.head.outcome == Excluded)
      // Both rules match
      assert(result.matches.head.exclusionIds.toSet == Set("synthetic-methods", "bridge-methods"))
    } finally {
      deleteRecursively(dir)
    }
  }

  test("printReport shows EXCLUDED section") {
    val matches = Seq(
      MatchedMethod("com.example.User", "copy", "(I)Lcom/example/User;", Excluded, Seq("case-copy"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.User", "equals", "(Ljava/lang/Object;)Z", Excluded, Seq("case-equals"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(2, 2, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)
    
    // Should have EXCLUDED section
    assert(lines.exists(_.contains("EXCLUDED")))
    assert(lines.exists(_.contains("com.example.User")))
    assert(lines.exists(_.contains("#copy")))
    assert(lines.exists(_.contains("rule-id:case-copy")))
  }

  test("printReport shows RESCUED section") {
    val matches = Seq(
      MatchedMethod("com.example.Config$", "apply", "(Lcom/example/Config;)Lcom/...;", Rescued, 
        Seq("comp-apply"), Seq("keep-config-apply"), Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)
    
    // Should have RESCUED section
    assert(lines.exists(_.contains("RESCUED")))
    assert(lines.exists(_.contains("com.example.Config$")))
    assert(lines.exists(_.contains("#apply")))
    assert(lines.exists(_.contains("excl:comp-apply")))
    assert(lines.exists(_.contains("incl:keep-config-apply")))
  }

  test("printReport shows both EXCLUDED and RESCUED sections") {
    val matches = Seq(
      MatchedMethod("com.example.User", "copy", "(I)Lcom/example/User;", Excluded, Seq("case-copy"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.Config$", "apply", "(Lcom/example/Config;)Lcom/...;", Rescued, 
        Seq("comp-apply"), Seq("keep-config-apply"), Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(2, 2, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)
    
    // Should have both sections
    assert(lines.exists(_.contains("EXCLUDED")))
    assert(lines.exists(_.contains("RESCUED")))
    assert(lines.exists(_.contains("Summary")))
    assert(lines.exists(_.contains("1 method excluded, 1 method rescued")))
  }

  test("printReport handles methods with no rule IDs") {
    val matches = Seq(
      MatchedMethod("test.Foo", "bar", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)
    
    // Should show method without rule-id suffix
    val methodLine = lines.find(_.contains("#bar"))
    assert(methodLine.isDefined)
    assert(!methodLine.get.contains("rule-id:"))
  }

  // --- ScanResult accessor tests ---

  test("excludedMethods returns only Excluded outcomes") {
    val matches = Seq(
      MatchedMethod("a.B", "foo", "()V", Excluded, Seq("id1"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("a.B", "bar", "()V", Rescued, Seq("id2"), Seq("id3"), Opcodes.ACC_PUBLIC),
      MatchedMethod("a.B", "baz", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 3, matches)
    val excluded = result.excludedMethods
    assert(excluded.size == 2)
    assert(excluded.forall(_.outcome == Excluded))
    assert(excluded.map(_.methodName).toSet == Set("foo", "baz"))
  }

  test("rescuedMethods returns only Rescued outcomes") {
    val matches = Seq(
      MatchedMethod("a.B", "foo", "()V", Excluded, Seq("id1"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("a.B", "bar", "()V", Rescued, Seq("id2"), Seq("id3"), Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 2, matches)
    val rescued = result.rescuedMethods
    assert(rescued.size == 1)
    assert(rescued.head.methodName == "bar")
    assert(rescued.head.outcome == Rescued)
  }

  test("excludedMethods and rescuedMethods return empty when no matches") {
    val result = ScanResult(5, 0, Seq.empty)
    assert(result.excludedMethods.isEmpty)
    assert(result.rescuedMethods.isEmpty)
  }

  // --- printReport edge cases ---

  test("printReport with empty matches shows only summary") {
    val result = ScanResult(3, 0, Seq.empty)
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    assert(!lines.exists(_.contains("EXCLUDED")))
    assert(!lines.exists(_.contains("RESCUED")))
    assert(lines.exists(_.contains("Summary")))
    assert(lines.exists(_.contains("0 methods excluded, 0 methods rescued")))
  }

  test("printReport zero-arg overload works") {
    // Just ensure the zero-arg overload doesn't throw
    val result = ScanResult(0, 0, Seq.empty)
    result.printReport()
  }

  test("printReport rescued method with no IDs shows (no-id)") {
    val matches = Seq(
      MatchedMethod("test.X", "run", "()V", Rescued, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    assert(lines.exists(_.contains("RESCUED")))
    val rescuedLine = lines.find(_.contains("#run")).get
    assert(rescuedLine.contains("excl:(no-id)"))
    assert(rescuedLine.contains("incl:(no-id)"))
  }

  test("printReport excluded methods are grouped and sorted by class") {
    val matches = Seq(
      MatchedMethod("z.B", "b", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("a.A", "a", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("a.A", "c", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(2, 3, matches)
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    // a.A should appear before z.B
    val classLines = lines.filter(l => l.contains("a.A") || l.contains("z.B"))
    assert(classLines.size >= 2)
    val aIdx = lines.indexWhere(_.contains("a.A"))
    val zIdx = lines.indexWhere(_.contains("z.B"))
    assert(aIdx < zIdx, "Classes should be sorted alphabetically")
  }

  // --- VerifyScanner scan edge cases ---

  test("scan with empty directory returns zero counts") {
    val dir = Files.createTempDirectory("verify-empty-")
    try {
      val result = VerifyScanner.scan(dir, Seq.empty)
      assert(result.classesScanned == 0)
      assert(result.totalMatched == 0)
      assert(result.matches.isEmpty)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan ignores non-class files") {
    val dir = Files.createTempDirectory("verify-non-class-")
    try {
      Files.write(dir.resolve("readme.txt"), "not a class file".getBytes)
      Files.write(dir.resolve("data.json"), "{}".getBytes)
      val result = VerifyScanner.scan(dir, Seq.empty)
      assert(result.classesScanned == 0)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan with multiple classes and matching rules") {
    val dir = Files.createTempDirectory("verify-multi-")
    try {
      createTestClass(dir, "pkg.A", Seq(
        ("copy", "()V", Opcodes.ACC_PUBLIC),
        ("process", "()V", Opcodes.ACC_PUBLIC)
      ))
      createTestClass(dir, "pkg.B", Seq(
        ("copy", "()V", Opcodes.ACC_PUBLIC)
      ))

      val rulesFile = tmpFile()
      write(rulesFile, Seq("pkg.*#copy(*) id:copy-rule"))
      val rules = Rules.load(rulesFile)

      val result = VerifyScanner.scan(dir, rules)
      assert(result.classesScanned == 2)
      // copy in pkg.A + copy in pkg.B = 2 matches (constructors don't match)
      assert(result.excludedMethods.size == 2)
      assert(result.excludedMethods.forall(_.methodName == "copy"))
    } finally {
      deleteRecursively(dir)
    }
  }

  // --- formatReport tests ---

  test("formatReport txt produces human-readable output without [verify] prefix") {
    val matches = Seq(
      MatchedMethod("com.example.Foo", "bar", "()V", Excluded, Seq("rule1"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val txt = result.formatReport("txt")
    assert(txt.contains("EXCLUDED"))
    assert(txt.contains("com.example.Foo"))
    assert(txt.contains("#bar()V"))
    assert(txt.contains("rule-id:rule1"))
    assert(txt.contains("Summary:"))
    assert(!txt.contains("[verify]"))
  }

  test("formatReport txt includes RESCUED section") {
    val matches = Seq(
      MatchedMethod("com.example.Bar", "apply", "()V", Rescued, Seq("excl"), Seq("incl"), Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val txt = result.formatReport("txt")
    assert(txt.contains("RESCUED"))
    assert(txt.contains("excl:excl"))
    assert(txt.contains("incl:incl"))
  }

  test("formatReport json produces valid JSON structure") {
    val matches = Seq(
      MatchedMethod("com.example.Foo", "bar", "()V", Excluded, Seq("rule1"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.Baz", "run", "()V", Rescued, Seq("excl-id"), Seq("incl-id"), Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(2, 2, matches)
    val json = result.formatReport("json")
    assert(json.contains("\"classesScanned\": 2"))
    assert(json.contains("\"excluded\""))
    assert(json.contains("\"rescued\""))
    assert(json.contains("\"com.example.Foo\""))
    assert(json.contains("\"bar\""))
    // Both excluded and rescued entries must use "exclusionRuleIds" (consistent schema)
    assert(json.contains("\"exclusionRuleIds\""), "excluded entries must use exclusionRuleIds key")
    assert(!json.contains("\"ruleIds\""), "legacy ruleIds key must not appear")
    assert(json.contains("\"rule1\""))
    assert(json.contains("\"com.example.Baz\""))
    assert(json.contains("\"excl-id\""))
    assert(json.contains("\"incl-id\""))
    assert(json.contains("\"inclusionRuleIds\""), "rescued entries must use inclusionRuleIds key")
  }

  test("formatReport json handles empty excluded and rescued") {
    val result = ScanResult(3, 0, Seq.empty)
    val json = result.formatReport("json")
    assert(json.contains("\"classesScanned\": 3"))
    assert(json.contains("\"excluded\": []"))
    assert(json.contains("\"rescued\": []"))
    assert(json.contains("\"unmatchedRules\": []"), "empty unmatchedRules must produce an empty JSON array key")
  }

  test("formatReport csv has correct header and rows") {
    val matches = Seq(
      MatchedMethod("com.example.Foo", "bar", "()V", Excluded, Seq("rule1"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.Baz", "run", "()V", Rescued, Seq("excl-id"), Seq("incl-id"), Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(2, 2, matches)
    val csv = result.formatReport("csv")
    val lines = csv.split("\n")
    assert(lines(0) == "outcome,class,method,descriptor,exclusionRuleIds,inclusionRuleIds")
    assert(lines.exists(l => l.startsWith("EXCLUDED,com.example.Foo,bar")))
    assert(lines.exists(l => l.startsWith("RESCUED,com.example.Baz,run")))
    assert(lines.exists(l => l.contains("rule1")))
    assert(lines.exists(l => l.contains("excl-id") && l.contains("incl-id")))
  }

  test("formatReport csv has only header when no matches") {
    val result = ScanResult(1, 0, Seq.empty)
    val csv = result.formatReport("csv")
    val lines = csv.split("\n").filter(_.nonEmpty)
    assert(lines.length == 1)
    assert(lines(0) == "outcome,class,method,descriptor,exclusionRuleIds,inclusionRuleIds")
  }

  test("formatReport throws IllegalArgumentException for unknown format") {
    val result = ScanResult(1, 0, Seq.empty)
    val ex = intercept[IllegalArgumentException] {
      result.formatReport("unknown")
    }
    assert(ex.getMessage.contains("unknown"))
    assert(ex.getMessage.contains("Supported"))
  }

  test("formatReport throws IllegalArgumentException for empty format string") {
    val result = ScanResult(1, 0, Seq.empty)
    intercept[IllegalArgumentException] {
      result.formatReport("")
    }
  }

  test("formatReport is case-insensitive for txt") {
    val result = ScanResult(1, 0, Seq.empty)
    val output = result.formatReport("TXT")
    assert(output.contains("Summary:"))
  }

  test("formatReport is case-insensitive for json") {
    val result = ScanResult(1, 0, Seq.empty)
    val output = result.formatReport("JSON")
    assert(output.contains("\"classesScanned\""))
  }

  test("formatReport is case-insensitive for csv") {
    val result = ScanResult(1, 0, Seq.empty)
    val output = result.formatReport("CSV")
    assert(output.contains("outcome,class,method,descriptor"))
  }

  test("formatReport txt shows multiple rule IDs comma-separated") {
    val matches = Seq(
      MatchedMethod("com.example.Foo", "copy", "()V", Excluded, Seq("rule-a", "rule-b", "rule-c"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val txt = result.formatReport("txt")
    assert(txt.contains("rule-id:rule-a,rule-b,rule-c"))
  }

  test("formatReport csv multiple exclusion rule IDs are pipe-separated") {
    val matches = Seq(
      MatchedMethod("com.example.Foo", "copy", "()V", Excluded, Seq("rule-a", "rule-b"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val csv = result.formatReport("csv")
    assert(csv.contains("rule-a|rule-b"))
  }

  test("formatReport csv rescued row has both exclusion and inclusion rule IDs pipe-separated") {
    val matches = Seq(
      MatchedMethod("com.example.Bar", "apply", "()V", Rescued, Seq("excl-1", "excl-2"), Seq("incl-1"), Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val csv = result.formatReport("csv")
    val row = csv.split("\n").find(_.startsWith("RESCUED")).getOrElse(fail("no RESCUED row"))
    assert(row.contains("excl-1|excl-2"))
    assert(row.contains("incl-1"))
  }

  test("formatReport json escapes double-quotes in class names") {
    val matches = Seq(
      MatchedMethod("com.example.Foo$\"inner\"", "bar", "()V", Excluded, Seq("r1"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val json = result.formatReport("json")
    // The class name contains a quote; must be escaped as \" in JSON string
    assert(json.contains("\\\"inner\\\""))
    assert(!json.contains("\"inner\""))  // raw unescaped quote would break JSON
  }

  test("formatReport json escapes backslash in field values") {
    val matches = Seq(
      MatchedMethod("com.example.Foo", "bar\\baz", "()V", Excluded, Seq("r1"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val json = result.formatReport("json")
    assert(json.contains("\"bar\\\\baz\""), "backslash must be escaped to \\\\ in JSON")
  }

  test("formatReport json escapes newline in field values") {
    val matches = Seq(
      MatchedMethod("com.example.Foo", "bar\nbaz", "()V", Excluded, Seq("r1"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    val json = result.formatReport("json")
    assert(json.contains("\"bar\\nbaz\""), "newline must be escaped to \\n in JSON")
    assert(!json.contains("\n  ]") || !json.contains("bar\nbaz"), "raw newline in string value would break JSON")
  }

  test("formatReport txt empty result shows only summary line") {
    val result = ScanResult(5, 0, Seq.empty)
    val txt = result.formatReport("txt")
    assert(!txt.contains("EXCLUDED"))
    assert(!txt.contains("RESCUED"))
    assert(txt.contains("Summary: 5 classes scanned, 0 methods excluded, 0 methods rescued"))
  }

  test("formatReport json entries are sorted by (fqcn, methodName, descriptor) for stable output") {
    // Provide matches in reverse-sorted order; output must be sorted
    val matches = Seq(
      MatchedMethod("com.example.Z", "zMethod", "()V", Excluded, Seq("r1"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.A", "mMethod", "()V", Excluded, Seq("r2"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.A", "aMethod", "()V", Excluded, Seq("r3"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(3, 3, matches)
    val json = result.formatReport("json")
    val aPos = json.indexOf("\"com.example.A\"")
    val zPos = json.lastIndexOf("\"com.example.Z\"")
    assert(aPos < zPos, "com.example.A entries must appear before com.example.Z in JSON output")
    // Within com.example.A: aMethod before mMethod
    val aMethodPos = json.indexOf("\"aMethod\"")
    val mMethodPos = json.indexOf("\"mMethod\"")
    assert(aMethodPos < mMethodPos, "aMethod must appear before mMethod in JSON output")
  }

  test("formatReport csv rows are sorted by (fqcn, methodName, descriptor) for stable output") {
    val matches = Seq(
      MatchedMethod("com.example.Z", "zMethod", "()V", Excluded, Seq("r1"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.A", "mMethod", "()V", Excluded, Seq("r2"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.A", "aMethod", "()V", Excluded, Seq("r3"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(3, 3, matches)
    val rows = result.formatReport("csv").split("\n").tail // drop header
    assert(rows(0).contains("com.example.A") && rows(0).contains("aMethod"), "first data row must be A/aMethod")
    assert(rows(1).contains("com.example.A") && rows(1).contains("mMethod"), "second data row must be A/mMethod")
    assert(rows(2).contains("com.example.Z") && rows(2).contains("zMethod"), "third data row must be Z/zMethod")
  }

  // --- UNMATCHED RULES section ---

  test("scan reports unmatched rules when no class matches the rule pattern") {
    val dir = Files.createTempDirectory("verify-unmatched-")
    try {
      createTestClass(dir, "test.Existing", Seq(("doWork", "()V", Opcodes.ACC_PUBLIC)))

      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "test.Existing#doWork(*) id:matched-rule",
        "test.NonExistent#copy(*) id:unmatched-rule"
      ))
      val rules = Rules.load(rulesFile)

      val result = VerifyScanner.scan(dir, rules)

      assert(result.unmatchedRules.size == 1)
      assert(result.unmatchedRules.head.id.contains("unmatched-rule"))
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan reports no unmatched rules when all rules match at least one method") {
    val dir = Files.createTempDirectory("verify-all-matched-")
    try {
      createTestClass(dir, "test.Example", Seq(
        ("copy", "()V", Opcodes.ACC_PUBLIC),
        ("apply", "()V", Opcodes.ACC_PUBLIC)
      ))

      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "test.Example#copy(*) id:copy-rule",
        "test.Example#apply(*) id:apply-rule"
      ))
      val rules = Rules.load(rulesFile)

      val result = VerifyScanner.scan(dir, rules)

      assert(result.unmatchedRules.isEmpty)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan with empty rules list has empty unmatchedRules") {
    val dir = Files.createTempDirectory("verify-empty-rules-")
    try {
      createTestClass(dir, "test.Foo", Seq(("bar", "()V", Opcodes.ACC_PUBLIC)))
      val result = VerifyScanner.scan(dir, Seq.empty)
      assert(result.unmatchedRules.isEmpty)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan exempts forward-compat rules from unmatched reporting") {
    val dir = Files.createTempDirectory("verify-fwd-compat-")
    try {
      createTestClass(dir, "test.Existing", Seq(("doWork", "()V", Opcodes.ACC_PUBLIC)))

      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "test.FutureClass#copy(*) forward-compat id:future-rule",
        "test.AnotherMissing#foo(*) id:plain-unmatched"
      ))
      val rules = Rules.load(rulesFile)

      val result = VerifyScanner.scan(dir, rules)

      // forward-compat rule is exempt; plain unmatched is reported
      assert(result.unmatchedRules.size == 1)
      assert(result.unmatchedRules.head.id.contains("plain-unmatched"))
      assert(!result.unmatchedRules.exists(_.id.contains("future-rule")))
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan with all-forward-compat unmatched rules reports empty unmatchedRules") {
    val dir = Files.createTempDirectory("verify-all-fwd-")
    try {
      createTestClass(dir, "test.Existing", Seq(("doWork", "()V", Opcodes.ACC_PUBLIC)))

      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "test.FutureClassA#copy(*) forward-compat id:future-a",
        "test.FutureClassB#apply(*) forward-compat id:future-b"
      ))
      val rules = Rules.load(rulesFile)

      val result = VerifyScanner.scan(dir, rules)

      assert(result.unmatchedRules.isEmpty)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("include rule that matches a method is not reported as unmatched") {
    val dir = Files.createTempDirectory("verify-include-matched-")
    try {
      createTestClass(dir, "test.Config$", Seq(
        ("apply", "(Ljava/lang/Object;)Ltest/Config;", Opcodes.ACC_PUBLIC)
      ))

      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "test.*$#apply(*) id:comp-apply",
        "+test.Config$#apply(*) id:keep-config-apply"
      ))
      val rules = Rules.load(rulesFile)

      val result = VerifyScanner.scan(dir, rules)

      // Both rules matched — neither should be in unmatchedRules
      assert(result.unmatchedRules.isEmpty)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("include rule that matches no methods is reported as unmatched") {
    val dir = Files.createTempDirectory("verify-include-unmatched-")
    try {
      createTestClass(dir, "test.Foo", Seq(("bar", "()V", Opcodes.ACC_PUBLIC)))

      val rulesFile = tmpFile()
      write(rulesFile, Seq(
        "+test.NonExistent$#apply(*) id:ghost-include"
      ))
      val rules = Rules.load(rulesFile)

      val result = VerifyScanner.scan(dir, rules)

      assert(result.unmatchedRules.size == 1)
      assert(result.unmatchedRules.head.id.contains("ghost-include"))
    } finally {
      deleteRecursively(dir)
    }
  }

  // --- UNMATCHED RULES in printReport ---

  test("printReport shows UNMATCHED RULES section when rules are unmatched") {
    val rule = Rules.parseLine("test.Missing#copy(*) id:missing-copy").get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))

    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    assert(lines.exists(_.contains("UNMATCHED RULES")))
    assert(lines.exists(_.contains("missing-copy")))
  }

  test("printReport shows rawText in UNMATCHED RULES section") {
    val rule = Rules.parseLine("test.Missing#copy(*) id:missing-copy").get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))

    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    // rawText should appear in the unmatched section
    assert(lines.exists(_.contains("test.Missing#copy(*)")))
  }

  test("printReport does not show UNMATCHED RULES section when all rules matched") {
    val result = ScanResult(1, 0, Seq.empty, Seq.empty)

    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    assert(!lines.exists(_.contains("UNMATCHED RULES")))
  }

  test("printReport UNMATCHED RULES uses [verify] prefix") {
    val rule = Rules.parseLine("test.X#foo(*) id:foo-rule").get
    val result = ScanResult(0, 0, Seq.empty, Seq(rule))

    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    val unmatchedLine = lines.find(_.contains("UNMATCHED RULES")).getOrElse(fail("missing section header"))
    assert(unmatchedLine.startsWith("[verify]"))
  }

  test("printReport includes forward-compat rules when explicitly placed in unmatchedRules") {
    val rule = Rules.parseLine("test.Future#copy(*) forward-compat id:future-rule").get
    // Normally forward-compat rules won't be in unmatchedRules (exempted by scan),
    // but if explicitly passed in ScanResult, they should still display.
    val result = ScanResult(0, 0, Seq.empty, Seq(rule))

    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    assert(lines.exists(_.contains("UNMATCHED RULES")))
    assert(lines.exists(_.contains("future-rule")))
  }

  test("printReport shows source info for global rules in UNMATCHED section") {
    val rule = Rules.parseLine("test.X#foo(*) id:foo-rule", GlobalSource("https://example.com/rules.txt")).get
    val result = ScanResult(0, 0, Seq.empty, Seq(rule))

    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)

    val ruleEntry = lines.find(_.contains("foo-rule")).getOrElse(fail("rule entry missing"))
    assert(ruleEntry.contains("global:") || ruleEntry.contains("[global"))
  }

  // --- UNMATCHED RULES in formatReport txt ---

  test("formatReport txt includes UNMATCHED RULES section") {
    val rule = Rules.parseLine("test.Missing#copy(*) id:missing-copy").get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))
    val txt = result.formatReport("txt")

    assert(txt.contains("UNMATCHED RULES"))
    assert(txt.contains("missing-copy"))
    assert(!txt.contains("[verify]"), "txt format must not have [verify] prefix")
  }

  test("formatReport txt does not include UNMATCHED RULES section when empty") {
    val result = ScanResult(1, 0, Seq.empty, Seq.empty)
    val txt = result.formatReport("txt")
    assert(!txt.contains("UNMATCHED RULES"))
  }

  // --- UNMATCHED RULES in formatReport json ---

  test("formatReport json includes unmatchedRules array") {
    val rule = Rules.parseLine("test.Missing#copy(*) id:missing-copy").get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))
    val json = result.formatReport("json")

    assert(json.contains("\"unmatchedRules\""))
    assert(json.contains("\"missing-copy\""))
  }

  test("formatReport json has empty unmatchedRules array when no unmatched rules") {
    val result = ScanResult(1, 0, Seq.empty, Seq.empty)
    val json = result.formatReport("json")

    assert(json.contains("\"unmatchedRules\": []"))
  }

  test("formatReport json unmatched entry has pattern, id, and source fields") {
    val rule = Rules.parseLine("test.Missing#copy(*) id:copy-rule", LocalSource("/path/rules.txt")).get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))
    val json = result.formatReport("json")

    assert(json.contains("\"pattern\""))
    assert(json.contains("\"id\""))
    assert(json.contains("\"source\""))
    assert(json.contains("copy-rule"))
    assert(json.contains("test.Missing#copy(*)"))
  }

  test("formatReport json unmatched entry has empty id string when rule has no id") {
    val rule = Rules.parseLine("test.Missing#copy(*)").get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))
    val json = result.formatReport("json")

    // id field should be present with empty value
    assert(json.contains("\"id\": \"\""))
  }

  // --- UNMATCHED RULES in formatReport csv ---

  test("formatReport csv includes UNMATCHED_RULE rows") {
    val rule = Rules.parseLine("test.Missing#copy(*) id:missing-copy").get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))
    val csv = result.formatReport("csv")

    assert(csv.contains("UNMATCHED_RULE"))
    assert(csv.contains("missing-copy"))
  }

  test("formatReport csv has no UNMATCHED_RULE rows when no unmatched rules") {
    val result = ScanResult(1, 0, Seq.empty, Seq.empty)
    val csv = result.formatReport("csv")
    assert(!csv.contains("UNMATCHED_RULE"))
  }

  test("formatReport csv UNMATCHED_RULE row has rawText in class column and id in exclusionRuleIds column") {
    val rule = Rules.parseLine("test.Missing#copy(*) id:missing-copy").get
    val result = ScanResult(1, 0, Seq.empty, Seq(rule))
    val csv = result.formatReport("csv")
    val rows = csv.split("\n")
    val unmatchedRow = rows.find(_.startsWith("UNMATCHED_RULE")).getOrElse(fail("no UNMATCHED_RULE row"))
    assert(unmatchedRow.contains("test.Missing#copy(*)"))
    assert(unmatchedRow.contains("missing-copy"))
  }

  // Helper to delete directory recursively
  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        val stream = Files.list(path)
        try {
          val iterator = stream.iterator()
          while (iterator.hasNext) {
            deleteRecursively(iterator.next())
          }
        } finally {
          stream.close()
        }
      }
      Files.delete(path)
    }
  }
}
