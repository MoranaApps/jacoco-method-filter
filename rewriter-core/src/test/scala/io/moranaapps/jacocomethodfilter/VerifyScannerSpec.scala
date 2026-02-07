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
      m.visitInsn(Opcodes.RETURN)
      m.visitMaxs(0, 1)
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
      assert(result.methodsMatched == 0)
      assert(result.matches.isEmpty)
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan matches methods against rules") {
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
      assert(result.methodsMatched == 1)
      assert(result.matches.size == 1)
      assert(result.matches.head.methodName == "copy")
      assert(result.matches.head.fqcn == "test.Example")
      assert(result.matches.head.ruleId.contains("case-copy"))
    } finally {
      deleteRecursively(dir)
    }
  }

  test("scan records multiple matches when method matches multiple rules") {
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
      assert(result.methodsMatched == 2) // Same method matched twice
      assert(result.matches.size == 2)
      assert(result.matches.map(_.ruleId).flatten.toSet == Set("synthetic-methods", "bridge-methods"))
    } finally {
      deleteRecursively(dir)
    }
  }

  test("printReport groups methods by class") {
    val matches = Seq(
      MatchedMethod("com.example.User", "copy", "(I)Lcom/example/User;", Some("case-copy")),
      MatchedMethod("com.example.User", "equals", "(Ljava/lang/Object;)Z", Some("case-equals")),
      MatchedMethod("com.example.Address", "hashCode", "()I", Some("case-hash"))
    )
    val result = ScanResult(2, 3, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)
    
    // Should group by FQCN
    assert(lines.exists(_.contains("com.example.Address")))
    assert(lines.exists(_.contains("com.example.User")))
    
    // Should show method signatures
    assert(lines.exists(_.contains("#copy")))
    assert(lines.exists(_.contains("#equals")))
    assert(lines.exists(_.contains("#hashCode")))
    
    // Should show rule IDs
    assert(lines.exists(_.contains("rule-id:case-copy")))
    assert(lines.exists(_.contains("rule-id:case-equals")))
    assert(lines.exists(_.contains("rule-id:case-hash")))
  }

  test("printReport does not include non-matching classes") {
    val matches = Seq(
      MatchedMethod("com.example.User", "copy", "(I)Lcom/example/User;", Some("case-copy"))
    )
    val result = ScanResult(5, 1, matches) // 5 scanned but only 1 match
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)
    
    // Should only mention the class with matches
    val classLines = lines.filter(_.startsWith("[verify] com."))
    assert(classLines.size == 1)
    assert(classLines.head.contains("com.example.User"))
  }

  test("printReport handles methods with no rule ID") {
    val matches = Seq(
      MatchedMethod("test.Foo", "bar", "()V", None)
    )
    val result = ScanResult(1, 1, matches)
    
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    result.printReport(line => lines += line)
    
    // Should show method without rule-id suffix
    val methodLine = lines.find(_.contains("#bar"))
    assert(methodLine.isDefined)
    assert(!methodLine.get.contains("rule-id:"))
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
