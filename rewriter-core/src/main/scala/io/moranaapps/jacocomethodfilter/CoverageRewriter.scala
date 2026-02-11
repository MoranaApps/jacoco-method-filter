package io.moranaapps.jacocomethodfilter

import io.moranaapps.jacocomethodfilter.Compat.using
import org.objectweb.asm._

import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._

private[jacocomethodfilter] final case class CliConfig(
                            in: Path   = Paths.get("target/scala-2.13/classes"),
                            out: Option[Path]  = None,
                            globalRules: Option[String] = None,
                            localRules: Option[Path] = None,
                            dryRun: Boolean = false,
                            verify: Boolean = false,
                            verifySuggestIncludes: Boolean = false
                          )

object CoverageRewriter {
  private val AnnotationDesc = "Lio/moranaapps/jacocomethodfilter/CoverageGenerated;"

  def main(args: Array[String]): Unit = {
    CoverageRewriterCli.parse(args) match {
      case Some(cfg) =>
        if (cfg.verify) verify(cfg) else run(cfg)
      case None =>
        sys.exit(2)
    }
  }

  private def run(cfg: CliConfig): Unit = {
    val rules = Rules.loadAll(cfg.globalRules, cfg.localRules)
    
    val rulesSummary = (cfg.globalRules, cfg.localRules) match {
      case (Some(g), Some(l)) => s"global: $g, local: $l"
      case (Some(g), None)    => s"global: $g"
      case (None, Some(l))    => s"local: $l"
      case _                  => "none"
    }
    println(s"[info] Loaded ${rules.size} rule(s) from $rulesSummary")

    val outPath = cfg.out.get // Safe because we validated it's present in main()
    Files.createDirectories(outPath)
    var files = 0
    var marked = 0

    using(Files.walk(cfg.in)) { s =>
      val it = s.iterator().asScala
      for {
        p <- it
        if Files.isRegularFile(p) && p.toString.endsWith(".class")
      } {
        files += 1
        val rel = cfg.in.relativize(p)
        val outFilePath = outPath.resolve(rel)
        Files.createDirectories(outFilePath.getParent)

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
                val resolution = RuleResolver.resolve(rules, fqcnDots, name, desc, access)
                if (resolution.shouldExclude && !alreadyAnnotated) {
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
        Files.write(outFilePath, outBytes)
      }
    }

    println(s"[info] Processed $files class file(s), marked $marked method(s). dry-run=${cfg.dryRun}")
  }

  private def verify(cfg: CliConfig): Unit = {
    val rules = Rules.loadAll(cfg.globalRules, cfg.localRules)
    
    val rulesSummary = (cfg.globalRules, cfg.localRules) match {
      case (Some(g), Some(l)) => s"global: $g, local: $l"
      case (Some(g), None)    => s"global: $g"
      case (None, Some(l))    => s"local: $l"
      case _                  => "none"
    }
    
    println(s"[verify] Active rules from $rulesSummary:")
    rules.zipWithIndex.foreach { case (rule, idx) =>
      val modeStr = rule.mode match {
        case Include => "+"
        case Exclude => "-"
      }
      val idStr = rule.id.map(id => s"id:$id").getOrElse("(no id)")
      val flagsStr = if (rule.flags.nonEmpty) s" [${rule.flags.mkString(",")}]" else ""
      val sourceStr = rule.source match {
        case GlobalSource(origin) => s" [global: $origin]"
        case LocalSource(path)    => s" [local: $path]"
        case _                    => ""
      }
      println(s"[verify]   ${idx + 1}. [$modeStr] $idStr$flagsStr$sourceStr")
    }
    
    val result = VerifyScanner.scan(cfg.in, rules)
    result.printReport(println, suggestIncludes = cfg.verifySuggestIncludes)
    
    println(s"[info] Verification complete: scanned ${result.classesScanned} class file(s), found ${result.totalMatched} method(s) matched by rules.")
  }
}
