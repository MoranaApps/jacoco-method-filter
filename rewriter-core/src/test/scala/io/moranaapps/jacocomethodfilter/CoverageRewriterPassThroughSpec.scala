package io.moranaapps.jacocomethodfilter

import org.objectweb.asm._
import org.scalatest.funsuite.AnyFunSuite

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path, Paths}

/** Pass-through behavior: with no rules configured, every class is copied unmodified and the
  * build does not fail (issue #69). Also covers `--require-rules` / `effectiveLocalRules`.
  */
class CoverageRewriterPassThroughSpec extends AnyFunSuite {

  private val AnnotationDesc = CoverageGenerated.AnnotationDescriptor

  private def newTempDir(prefix: String): Path = {
    val p = Files.createTempDirectory(prefix)
    p.toFile.deleteOnExit()
    p
  }

  /** Write a trivial class with the given `()V` methods into `dir`. */
  private def createTestClass(dir: Path, className: String, methodNames: Seq[String]): Unit = {
    val cw = new ClassWriter(ClassWriter.COMPUTE_MAXS)
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className.replace('.', '/'), null, "java/lang/Object", null)

    val ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    ctor.visitCode()
    ctor.visitVarInsn(Opcodes.ALOAD, 0)
    ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    ctor.visitInsn(Opcodes.RETURN)
    ctor.visitMaxs(0, 0)
    ctor.visitEnd()

    methodNames.foreach { name =>
      val m = cw.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
      m.visitCode()
      m.visitInsn(Opcodes.RETURN)
      m.visitMaxs(0, 0)
      m.visitEnd()
    }

    cw.visitEnd()
    val classFile = dir.resolve(className.replace('.', '/') + ".class")
    Files.createDirectories(classFile.getParent)
    Files.write(classFile, cw.toByteArray)
  }

  /** Names of methods carrying `@CoverageGenerated` in the class file at `path`. */
  private def annotatedMethods(path: Path): Set[String] = {
    val found = scala.collection.mutable.Set.empty[String]
    val cr = new ClassReader(Files.readAllBytes(path))
    cr.accept(new ClassVisitor(Opcodes.ASM9) {
      override def visitMethod(access: Int, name: String, descriptor: String,
                               signature: String, exceptions: Array[String]): MethodVisitor =
        new MethodVisitor(Opcodes.ASM9) {
          override def visitAnnotation(d: String, visible: Boolean): AnnotationVisitor = {
            if (d == AnnotationDesc) found += name
            null
          }
        }
    }, 0)
    found.toSet
  }

  /** All `.class` files under `dir`, as paths relative to `dir` with `/` separators. */
  private def relativeClassFiles(dir: Path): Vector[String] = {
    val buf = Vector.newBuilder[Path]
    def recurse(p: Path): Unit = {
      if (Files.isDirectory(p)) {
        val s = Files.list(p)
        try { val it = s.iterator(); while (it.hasNext) recurse(it.next()) }
        finally s.close()
      } else if (p.toString.endsWith(".class")) buf += p
    }
    recurse(dir)
    buf.result().map(p => dir.relativize(p).toString.replace('\\', '/')).sorted
  }

  test("run with no rules writes an unmodified pass-through copy of every class") {
    val in  = newTempDir("jmf-passthru-in-")
    val out = newTempDir("jmf-passthru-out-")
    createTestClass(in, "pkg.Alpha", Seq("copy", "equals", "hashCode"))
    createTestClass(in, "pkg.sub.Beta", Seq("toString"))

    val stdout = new ByteArrayOutputStream()
    Console.withOut(stdout) {
      CoverageRewriter.run(CliConfig(in = in, out = Some(out)), out)
    }

    assert(relativeClassFiles(out) == Vector("pkg/Alpha.class", "pkg/sub/Beta.class"))
    assert(annotatedMethods(out.resolve("pkg/Alpha.class")).isEmpty)
    assert(annotatedMethods(out.resolve("pkg/sub/Beta.class")).isEmpty)

    val log = stdout.toString
    assert(log.contains("Loaded 0 rule(s) from none"), log)
    assert(log.contains("marked 0 method(s)"), log)
  }

  test("run with a missing --local-rules file warns and still produces a pass-through copy") {
    val in  = newTempDir("jmf-passthru-in-")
    val out = newTempDir("jmf-passthru-out-")
    createTestClass(in, "pkg.Gamma", Seq("copy"))
    val missingRules = Paths.get(newTempDir("jmf-rules-").toString, "absent-rules.txt")

    val stdout = new ByteArrayOutputStream()
    Console.withOut(stdout) {
      CoverageRewriter.run(CliConfig(in = in, out = Some(out), localRules = Some(missingRules)), out)
    }

    assert(relativeClassFiles(out) == Vector("pkg/Gamma.class"))
    assert(annotatedMethods(out.resolve("pkg/Gamma.class")).isEmpty)
    assert(stdout.toString.contains("local rules file not found"), stdout.toString)
  }

  test("verify with no rules completes and reports zero matches") {
    val in = newTempDir("jmf-verify-in-")
    createTestClass(in, "pkg.Delta", Seq("copy"))

    val stdout = new ByteArrayOutputStream()
    Console.withOut(stdout) {
      CoverageRewriter.verify(CliConfig(in = in, verify = true))
    }
    assert(stdout.toString.contains("found 0 method(s) matched by rules"), stdout.toString)
  }

  test("effectiveLocalRules returns None and warns when the path does not exist") {
    val missing = Paths.get(newTempDir("jmf-rules-").toString, "nope.txt")
    val stdout = new ByteArrayOutputStream()
    val result = Console.withOut(stdout) { CoverageRewriter.effectiveLocalRules(Some(missing)) }
    assert(result.isEmpty)
    assert(stdout.toString.contains("local rules file not found"))
  }

  test("effectiveLocalRules passes an existing path (and None) through unchanged") {
    val existing = Files.createTempFile("jmf-rules-", ".txt")
    existing.toFile.deleteOnExit()
    assert(CoverageRewriter.effectiveLocalRules(Some(existing)).contains(existing))
    assert(CoverageRewriter.effectiveLocalRules(None).isEmpty)
  }
}
