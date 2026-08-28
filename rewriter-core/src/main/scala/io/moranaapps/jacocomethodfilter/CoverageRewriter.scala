package io.moranaapps.jacocomethodfilter

import io.moranaapps.jacocomethodfilter.Compat._
import org.objectweb.asm._

import java.nio.file.{Files, Path, Paths}

/** Configuration for the jacoco-method-filter CLI.
  *
  * @param in Input classes directory to scan
  * @param out Output classes directory (optional in verify mode)
  * @param globalRules Global rules file path or URL. Optional — with no rules source at all, every
  *                    class passes through unfiltered (no-op).
  * @param localRules Local rules file path. Optional; a path that does not exist is treated as
  *                   empty (a warning is emitted).
  * @param dryRun If true, print matches without modifying classes
  * @param verify If true, run read-only scan mode
  * @param reportFile Optional path to write the filtered-methods report (txt/json/csv)
  * @param reportFormat Report format: txt (default), json, or csv
  * @param errorOnUnmatched If true, exit non-zero when any rules matched zero methods (requires verify mode)
  * @param strict If true, exit non-zero when any rules have no id: label
  * @param requireRules If true, exit non-zero when no rules source is configured (restores pre-2.x behavior)
  */
private[jacocomethodfilter] final case class CliConfig(
  in: Path = Paths.get("."),
  out: Option[Path] = None,
  globalRules: Option[String] = None,
  localRules: Option[Path] = None,
  dryRun: Boolean = false,
  verify: Boolean = false,
  reportFile: Option[Path] = None,
  reportFormat: String = "txt",
  errorOnUnmatched: Boolean = false,
  strict: Boolean = false,
  requireRules: Boolean = false
)

object CoverageRewriter {
  private val AnnotationDesc = CoverageGenerated.AnnotationDescriptor

  def main(args: Array[String]): Unit = {
    CoverageRewriterCli.parse(args) match {
      case Some(cfg) =>
        if (cfg.verify) verify(cfg)
        else {
          cfg.out match {
            case Some(outPath) => run(cfg, outPath)
            case None          => sys.exit(2)
          }
        }
      case None =>
        sys.exit(2)
    }
  }

  private[jacocomethodfilter] def run(cfg: CliConfig, outPath: Path): Unit = {
    val localRules = effectiveLocalRules(cfg.localRules)
    abortIfRulesRequired(cfg, localRules)

    val rules = Rules.loadAll(cfg.globalRules, localRules)
    println(s"[info] Loaded ${rules.size} rule(s) from ${rulesSummary(cfg.globalRules, localRules)}")

    abortIfUnlabelled(rules, cfg)

    Files.createDirectories(outPath)
    var files = 0
    var marked = 0

    walkClassFiles(cfg.in) { p =>
      files += 1
      val outFilePath = outPath.resolve(cfg.in.relativize(p))
      Files.createDirectories(outFilePath.getParent)
      marked += rewriteClassFile(p, outFilePath, rules, cfg.dryRun)
    }

    println(s"[info] Processed $files class file(s), marked $marked method(s). dry-run=${cfg.dryRun}")

    cfg.reportFile.foreach { path =>
      // TODO(perf): avoid second scan by collecting MatchedMethod data during the rewrite pass above
      // (each rewriteClassFile call already invokes RuleResolver.resolve per method)
      val result = VerifyScanner.scan(cfg.in, rules)
      writeReportFile(path, result.formatReport(cfg.reportFormat))
    }
  }

  private[jacocomethodfilter] def verify(cfg: CliConfig): Unit = {
    val localRules = effectiveLocalRules(cfg.localRules)
    abortIfRulesRequired(cfg, localRules)

    val rules = Rules.loadAll(cfg.globalRules, localRules)

    abortIfUnlabelled(rules, cfg)

    println(s"[verify] Active rules from ${rulesSummary(cfg.globalRules, localRules)}:")
    printRulesListing(rules)

    val result = VerifyScanner.scan(cfg.in, rules)
    result.printReport(println)

    println(s"[info] Verification complete: scanned ${result.classesScanned} class file(s), found ${result.totalMatched} method(s) matched by rules.")

    if (result.unmatchedRules.nonEmpty && !cfg.errorOnUnmatched) {
      println(s"[warn] ${result.unmatchedRules.size} rule(s) matched zero methods. Use --error-on-unmatched to enforce this as a build error.")
    }

    cfg.reportFile.foreach { path =>
      writeReportFile(path, result.formatReport(cfg.reportFormat))
    }

    if (cfg.errorOnUnmatched && result.unmatchedRules.nonEmpty) {
      println(s"[error] Aborting: ${result.unmatchedRules.size} unmatched rule(s) found (--error-on-unmatched is set).")
      sys.exit(1)
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private def abortIfUnlabelled(rules: Seq[MethodRule], cfg: CliConfig): Unit = {
    if (cfg.strict) {
      val unlabelledCount = rules.count(_.id.isEmpty)
      if (unlabelledCount > 0) {
        println(s"[error] Aborting: $unlabelledCount rule(s) have no id: label (--strict is set).")
        sys.exit(1)
      }
    }
  }

  /** Resolve the local rules path for loading.
    *
    * A `--local-rules` path that does not exist is a soft condition: method filtering is opt-in,
    * so we warn and proceed with zero local rules rather than aborting. (A missing `--global-rules`
    * source stays a hard error — it is always explicitly configured.)
    */
  private[jacocomethodfilter] def effectiveLocalRules(localRules: Option[Path]): Option[Path] =
    localRules match {
      case Some(p) if !Files.exists(p) =>
        println(s"[warn] local rules file not found: $p — proceeding with 0 local rules")
        None
      case other => other
    }

  /** With `--require-rules`, abort when no rules source is available (restores pre-2.x behavior). */
  private def abortIfRulesRequired(cfg: CliConfig, localRules: Option[Path]): Unit = {
    if (cfg.requireRules && cfg.globalRules.isEmpty && localRules.isEmpty) {
      println("[error] Aborting: --require-rules is set but no rules source is configured " +
        "(no --global-rules, no --local-rules file).")
      sys.exit(1)
    }
  }

  private def writeReportFile(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    println(s"[info] Report written to: $path")
  }

  /** Human-readable description of the configured rule sources. */
  private def rulesSummary(globalRules: Option[String], localRules: Option[Path]): String =
    (globalRules, localRules) match {
      case (Some(g), Some(l)) => s"global: $g, local: $l"
      case (Some(g), None)    => s"global: $g"
      case (None, Some(l))    => s"local: $l"
      case _                  => "none"
    }

  /** Walk `root` for `.class` files and invoke `f` on each. */
  private def walkClassFiles(root: Path)(f: Path => Unit): Unit =
    using(Files.walk(root)) { stream =>
      val it = stream.iterator().asScala
      for {
        p <- it
        if Files.isRegularFile(p) && p.toString.endsWith(".class")
      } f(p)
    }

  /** Rewrite a single class file: inject `@CoverageGenerated` on matched methods.
    *
    * @return the number of methods marked in this file
    */
  private def rewriteClassFile(inPath: Path, outPath: Path, rules: Seq[MethodRule], dryRun: Boolean): Int = {
    val inBytes = Files.readAllBytes(inPath)
    val cr = new ClassReader(inBytes)
    val cw = new ClassWriter(0)
    var marked = 0

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
              if (dryRun) {
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
    val outBytes = if (dryRun) inBytes else cw.toByteArray
    Files.write(outPath, outBytes)
    marked
  }

  /** Print a numbered listing of rules for verify output. */
  private def printRulesListing(rules: Seq[MethodRule]): Unit =
    rules.zipWithIndex.foreach { case (rule, idx) =>
      val modeStr = rule.mode match {
        case Include => "+"
        case Exclude => "-"
      }
      val idStr = rule.id.map(id => s"id:$id").getOrElse("(no id)")
      val flagsStr = if (rule.flags.nonEmpty) s" [${rule.flags.mkString(",")}]" else ""
      val fwdCompatStr = if (rule.forwardCompat) " (forward-compat)" else ""
      val sourceStr = rule.source match {
        case GlobalSource(origin) => s" [global: $origin]"
        case LocalSource(path)    => s" [local: $path]"
        case _                    => ""
      }
      println(s"[verify]   ${idx + 1}. [$modeStr] $idStr$flagsStr$fwdCompatStr$sourceStr")
    }
}
