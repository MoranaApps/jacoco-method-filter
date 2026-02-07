package io.moranaapps.jacocomethodfilter

import io.moranaapps.jacocomethodfilter.Compat.using
import org.objectweb.asm._

import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import scala.collection.mutable

final case class MatchedMethod(
                                 fqcn: String,
                                 methodName: String,
                                 descriptor: String,
                                 ruleId: Option[String]
                               )

final case class ScanResult(
                              classesScanned: Int,
                              methodsMatched: Int,
                              matches: Seq[MatchedMethod]
                            ) {
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
    // Group by class
    val byClass = matches.groupBy(_.fqcn).toSeq.sortBy(_._1)
    
    byClass.foreach { case (fqcn, methods) =>
      out(s"[verify] $fqcn")
      methods.sortBy(m => (m.methodName, m.descriptor)).foreach { m =>
        val ruleIdStr = m.ruleId.map(id => s"  rule-id:$id").getOrElse("")
        out(s"[verify]   #${m.methodName}${m.descriptor}$ruleIdStr")
      }
    }
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
            // Check all rules for a match.
            // Note: A method can match multiple rules, and we record each match separately.
            // This helps users understand rule overlap and prioritize/refine their rules.
            rules.foreach { rule =>
              if (Rules.matches(rule, fqcnDots, name, desc, access)) {
                matchedMethods += MatchedMethod(fqcnDots, name, desc, rule.id)
              }
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
