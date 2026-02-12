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
    assert(lines.exists(_.contains("1 methods excluded, 1 methods rescued")))
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

  // --- MethodClassifier tests ---

  test("MethodClassifier.classify - synthetic method -> StronglyGenerated") {
    val access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC
    val result = MethodClassifier.classify("someMethod", access)
    assert(result == StronglyGenerated)
  }

  test("MethodClassifier.classify - bridge method -> StronglyGenerated") {
    val access = Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE
    val result = MethodClassifier.classify("someMethod", access)
    assert(result == StronglyGenerated)
  }

  test("MethodClassifier.classify - method named $anonfun$something -> StronglyGenerated") {
    val access = Opcodes.ACC_PUBLIC
    val result = MethodClassifier.classify("$anonfun$myFunc$1", access)
    assert(result == StronglyGenerated)
  }

  test("MethodClassifier.classify - method named lambda$something -> StronglyGenerated") {
    val access = Opcodes.ACC_PUBLIC
    val result = MethodClassifier.classify("lambda$main$0", access)
    assert(result == StronglyGenerated)
  }

  test("MethodClassifier.classify - method named access$000 -> StronglyGenerated") {
    val access = Opcodes.ACC_PUBLIC
    val result = MethodClassifier.classify("access$000", access)
    assert(result == StronglyGenerated)
  }

  test("MethodClassifier.classify - regular methods like apply, process, getData -> PossiblyHuman") {
    val access = Opcodes.ACC_PUBLIC
    assert(MethodClassifier.classify("apply", access) == PossiblyHuman)
    assert(MethodClassifier.classify("process", access) == PossiblyHuman)
    assert(MethodClassifier.classify("getData", access) == PossiblyHuman)
  }

  // --- printReport with suggestions tests ---

  test("printReport with suggestIncludes=true shows suggestions for possibly-human excluded methods") {
    val matches = Seq(
      MatchedMethod("com.example.User", "apply", "(I)Lcom/example/User;", Excluded, Seq("case-apply"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.User", "process", "()V", Excluded, Seq("user-methods"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 2, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line, suggestIncludes = true)
    
    // Should have suggestions section
    assert(lines.exists(_.contains("Suggested include rules")))
    assert(lines.exists(_.contains("+com.example.User#apply(*)")))
    assert(lines.exists(_.contains("+com.example.User#process(*)")))
    assert(lines.exists(_.contains("best-effort heuristics")))
    assert(lines.exists(_.contains("Human vs generated")))
  }

  test("printReport with suggestIncludes=true does NOT suggest rules for synthetic/bridge excluded methods") {
    val matches = Seq(
      MatchedMethod("com.example.User", "$anonfun$apply$1", "(I)Lcom/example/User;", Excluded, Seq("anon"), Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.User", "bridgeMethod", "()V", Excluded, Seq("bridge"), Seq.empty, Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE)
    )
    val result = ScanResult(1, 2, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line, suggestIncludes = true)
    
    // Should NOT have suggestions section (all methods are generated)
    assert(!lines.exists(_.contains("Suggested include rules")))
  }

  test("printReport with suggestIncludes=false (default) does NOT show the suggestions section") {
    val matches = Seq(
      MatchedMethod("com.example.User", "apply", "(I)Lcom/example/User;", Excluded, Seq("case-apply"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line, suggestIncludes = false)
    
    // Should NOT have suggestions section
    assert(!lines.exists(_.contains("Suggested include rules")))
    assert(!lines.exists(_.contains("+com.example.User#apply(*)")))
  }

  test("printReport default (no args) does NOT show suggestions") {
    val matches = Seq(
      MatchedMethod("com.example.User", "apply", "(I)Lcom/example/User;", Excluded, Seq("case-apply"), Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 1, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line) // No suggestIncludes parameter
    
    // Should NOT have suggestions section
    assert(!lines.exists(_.contains("Suggested include rules")))
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

  test("printReport suggestIncludes with mix of human and generated methods") {
    val matches = Seq(
      MatchedMethod("com.example.Svc", "process", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.Svc", "$anonfun$apply$1", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC),
      MatchedMethod("com.example.Svc", "handle", "()V", Excluded, Seq.empty, Seq.empty, Opcodes.ACC_PUBLIC)
    )
    val result = ScanResult(1, 3, matches)
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line, suggestIncludes = true)

    // Only process and handle should be suggested, not $anonfun$apply$1
    assert(lines.exists(_.contains("+com.example.Svc#process(*)")))
    assert(lines.exists(_.contains("+com.example.Svc#handle(*)")))
    assert(!lines.exists(_.contains("+com.example.Svc#$anonfun$apply$1(*)")))
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
