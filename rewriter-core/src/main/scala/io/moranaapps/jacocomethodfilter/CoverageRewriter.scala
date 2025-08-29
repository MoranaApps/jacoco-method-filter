package io.moranaapps.jacocomethodfilter

import org.objectweb.asm._
import java.nio.file.{Files, Path, Paths}
import scala.jdk.StreamConverters._
import scopt.OParser

final case class CliConfig(
                            in: Path   = Paths.get("target/scala-2.13/classes"),
                            out: Path  = Paths.get("target/scala-2.13/classes-filtered"),
                            rules: Path = Paths.get("rules/coverage-rules.sample.txt"),
                            dryRun: Boolean = false
                          )

object CoverageRewriter {
  private val AnnotationDesc = "Lio/moranaapps/jacocomethodfilter/CoverageGenerated;"

  def main(args: Array[String]): Unit = {
    val b = OParser.builder[CliConfig]
    val parser = {
      import b._
      OParser.sequence(
        programName("jacoco-method-filter"),
        opt[String]("in").required().action((v,c) => c.copy(in = Paths.get(v))).text("Input classes directory"),
        opt[String]("out").required().action((v,c) => c.copy(out = Paths.get(v))).text("Output classes directory"),
        opt[String]("rules").required().action((v,c) => c.copy(rules = Paths.get(v))).text("Rules file path"),
        opt[Unit]("dry-run").action((_,c) => c.copy(dryRun = true)).text("Only print matches; do not modify classes")
      )
    }

    OParser.parse(parser, args, CliConfig()) match {
      case Some(cfg) => run(cfg)
      case None      => sys.exit(2)
    }
  }

  private def run(cfg: CliConfig): Unit = {
    val rules = Rules.load(cfg.rules)
    println(s"[info] Loaded ${rules.size} rule(s) from ${cfg.rules}")

    Files.createDirectories(cfg.out)
    var files = 0
    var marked = 0

    using(Files.walk(cfg.in)) { s =>
      val it = s.iterator().asScal
      for {
        p <- it
        if Files.isRegularFile(p) && p.toString.endsWith(".class")
      } {
        files += 1
        val rel = cfg.in.relativize(p)
        val outPath = cfg.out.resolve(rel)
        Files.createDirectories(outPath.getParent)

        val inBytes = Files.readAllBytes(p)
        val cr = new ClassReader(inBytes)
        val cw = new ClassWriter(0)

        var fqcnDots = ""
        val cv = new ClassVisitor(Opcodes.ASM9, cw) {
          override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]): Unit = {
            fqcnDots = name.replace('/', '.')
            super.visit(version, access, name, signature, superName, interfaces)
          }

          override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
            val base = super.visitMethod(access, name, desc, signature, exceptions)
            new MethodVisitor(Opcodes.ASM9, base) {
              private var alreadyAnnotated = false

              override def visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor = {
                if (descriptor == AnnotationDesc) alreadyAnnotated = true
                super.visitAnnotation(descriptor, visible)
              }

              override def visitEnd(): Unit = {
                val matchRule = rules.exists(r => Rules.matches(r, fqcnDots, name, desc, access))
                if (matchRule && !alreadyAnnotated) {
                  if (cfg.dryRun) {
                    println(s"[match] $fqcnDots#$name$desc")
                  } else {
                    val av = super.visitAnnotation(AnnotationDesc, false) // Retention CLASS
                    if (av != null) av.visitEnd()
                  }
                  marked += 1
                }
                super.visitEnd()
              }
            }
          }
        }

        cr.accept(cv, 0)
        val outBytes = if (cfg.dryRun) inBytes else cw.toByteArray
        Files.write(outPath, outBytes)
      }
    }

    println(s"[info] Processed $files class file(s), marked $marked method(s). dry-run=${cfg.dryRun}")
  }
}
