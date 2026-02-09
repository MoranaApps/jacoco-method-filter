package io.moranaapps.jacocomethodfilter

import io.moranaapps.jacocomethodfilter.Compat.using
import org.objectweb.asm._

import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
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
                                 inclusionIds: Seq[String]
                               )

final case class ScanResult(
                              classesScanned: Int,
                              totalMatched: Int,
                              matches: Seq[MatchedMethod]
                            ) {
  def excludedMethods: Seq[MatchedMethod] = matches.filter(_.outcome == Excluded)
  def rescuedMethods: Seq[MatchedMethod] = matches.filter(_.outcome == Rescued)
  
  /**
   * Emit a human-readable report of matched methods.
   * Default behavior: print to standard output.
   */
  def printReport(): Unit = printReport(println)

  /**
   * Emit a human-readable report of matched methods.
   *
   * @param out sink for each formatted output line
   */
  def printReport(out: String => Unit): Unit = {
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
              matchedMethods += MatchedMethod(fqcnDots, name, desc, Excluded, exclusionIds, Seq.empty)
            } else if (resolution.isRescued) {
              val exclusionIds = resolution.exclusions.flatMap(_.id)
              val inclusionIds = resolution.inclusions.flatMap(_.id)
              matchedMethods += MatchedMethod(fqcnDots, name, desc, Rescued, exclusionIds, inclusionIds)
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
