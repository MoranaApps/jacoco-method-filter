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
