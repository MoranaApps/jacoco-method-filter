package io.moranaapps.jacocomethodfilter

import io.moranaapps.jacocomethodfilter.Compat._
import org.objectweb.asm._

import java.nio.file.{Files, Path}
import scala.collection.mutable

sealed trait MethodOutcome
case object Excluded extends MethodOutcome
case object Rescued extends MethodOutcome

final case class MatchedMethod(
                                 fqcn: String,
                                 methodName: String,
                                 descriptor: String,
                                 outcome: MethodOutcome,
                                 exclusionIds: Seq[String],
                                 inclusionIds: Seq[String],
                                 access: Int
                               )

final case class ScanResult(
                              classesScanned: Int,
                              totalMatched: Int,
                              matches: Seq[MatchedMethod]
                            ) {
  def excludedMethods: Seq[MatchedMethod] = matches.filter(_.outcome == Excluded)
  def rescuedMethods: Seq[MatchedMethod] = matches.filter(_.outcome == Rescued)
  
  /** Print report to stdout, no suggestions. */
  def printReport(): Unit = printReport(println, suggestIncludes = false)

  /** Print report without suggestions. */
  def printReport(out: String => Unit): Unit = printReport(out, suggestIncludes = false)

  /** Print a report of matched methods. */
  def printReport(out: String => Unit, suggestIncludes: Boolean): Unit = {
    // Print excluded methods
    val excluded = excludedMethods
    if (excluded.nonEmpty) {
      out(s"[verify] EXCLUDED (${excluded.size} methods):")
      val byClass = excluded.groupBy(_.fqcn).toSeq.sortBy(_._1)
      byClass.foreach { case (fqcn, methods) =>
        out(s"[verify]   $fqcn")
        methods.sortBy(m => (m.methodName, m.descriptor)).foreach { m =>
          val ruleIdStr = if (m.exclusionIds.nonEmpty) {
            s"  rule-id:${m.exclusionIds.mkString(",")}"
          } else {
            ""
          }
          out(s"[verify]     #${m.methodName}${m.descriptor}$ruleIdStr")
        }
      }
      out("")
    }
    
    // Print rescued methods
    val rescued = rescuedMethods
    if (rescued.nonEmpty) {
      out(s"[verify] RESCUED by include rules (${rescued.size} methods):")
      val byClass = rescued.groupBy(_.fqcn).toSeq.sortBy(_._1)
      byClass.foreach { case (fqcn, methods) =>
        out(s"[verify]   $fqcn")
        methods.sortBy(m => (m.methodName, m.descriptor)).foreach { m =>
          val exclStr = if (m.exclusionIds.nonEmpty) m.exclusionIds.mkString(",") else "(no-id)"
          val inclStr = if (m.inclusionIds.nonEmpty) m.inclusionIds.mkString(",") else "(no-id)"
          out(s"[verify]     #${m.methodName}${m.descriptor}  excl:$exclStr → incl:$inclStr")
        }
      }
      out("")
    }
    
    // Print suggested include rules for possibly-human excluded methods
    if (suggestIncludes && excluded.nonEmpty) {
      val possiblyHuman = excluded.filter { m =>
        MethodClassifier.classify(m.methodName, m.access) == PossiblyHuman
      }
      
      if (possiblyHuman.nonEmpty) {
        out(s"[verify] Suggested include rules (heuristic — review before use):")
        possiblyHuman.sortBy(m => (m.fqcn, m.methodName)).foreach { m =>
          out(s"[verify]   +${m.fqcn}#${m.methodName}(*)")
        }
        out("")
        out(s"[verify] NOTE: These suggestions are best-effort heuristics based on bytecode analysis.")
        out("""[verify]       "Human vs generated" cannot be determined perfectly from bytecode.""")
        out("")
      }
    }
    
    out(s"[verify] Summary: $classesScanned classes scanned, ${excluded.size} methods excluded, ${rescued.size} methods rescued")
  }
}

object VerifyScanner {
  def scan(classesDir: Path, rules: Seq[MethodRule]): ScanResult = {
    var classesScanned = 0
    val matchedMethods = mutable.ListBuffer.empty[MatchedMethod]

    using(Files.walk(classesDir)) { stream =>
      val it = stream.iterator().asScala
      for {
        p <- it
        if Files.isRegularFile(p) && p.toString.endsWith(".class")
      } {
        classesScanned += 1
        val inBytes = Files.readAllBytes(p)
        val cr = new ClassReader(inBytes)

        var fqcnDots = ""
        val cv = new ClassVisitor(Opcodes.ASM9) {
          override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]): Unit = {
            fqcnDots = name.replace('/', '.')
            super.visit(version, access, name, signature, superName, interfaces)
          }

          override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
            // Use RuleResolver to determine outcome
            val resolution = RuleResolver.resolve(rules, fqcnDots, name, desc, access)
            
            if (resolution.shouldExclude) {
              val exclusionIds = resolution.exclusions.flatMap(_.id)
              matchedMethods += MatchedMethod(fqcnDots, name, desc, Excluded, exclusionIds, Seq.empty, access)
            } else if (resolution.isRescued) {
              val exclusionIds = resolution.exclusions.flatMap(_.id)
              val inclusionIds = resolution.inclusions.flatMap(_.id)
              matchedMethods += MatchedMethod(fqcnDots, name, desc, Rescued, exclusionIds, inclusionIds, access)
            }
            null // We don't need to visit method body
          }
        }

        cr.accept(cv, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
      }
    }

    ScanResult(classesScanned, matchedMethods.size, matchedMethods.toSeq)
  }
}
