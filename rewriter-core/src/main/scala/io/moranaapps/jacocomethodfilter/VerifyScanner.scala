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
                              matches: Seq[MatchedMethod],
                              unmatchedRules: Seq[MethodRule] = Seq.empty
                            ) {
  def excludedMethods: Seq[MatchedMethod] = matches.filter(_.outcome == Excluded)
  def rescuedMethods: Seq[MatchedMethod] = matches.filter(_.outcome == Rescued)

  private def plural(n: Int, word: String): String = {
    val pluralForm = if (word == "class") "classes" else word + "s"
    if (n == 1) s"$n $word" else s"$n $pluralForm"
  }

  private def formatUnmatchedRuleEntry(r: MethodRule): String = {
    val pattern = if (r.rawText.nonEmpty) r.rawText else "(pattern unavailable)"
    val idStr = r.id.map(id => s"  id:$id").getOrElse("  (no id)")
    val sourceStr = r.source match {
      case GlobalSource(origin)              => s"  [global: $origin]"
      case LocalSource(path) if path.nonEmpty => s"  [local: $path]"
      case _                                 => ""
    }
    s"$pattern$idStr$sourceStr"
  }

  /** Print report to stdout. */
  def printReport(): Unit = printReport(println)

  /** Print a report of matched methods. */
  def printReport(out: String => Unit): Unit = {
    val excluded = excludedMethods
    if (excluded.nonEmpty) {
      out(s"[verify] EXCLUDED (${plural(excluded.size, "method")}):")
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

    val rescued = rescuedMethods
    if (rescued.nonEmpty) {
      out(s"[verify] RESCUED by include rules (${plural(rescued.size, "method")}):")
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

    val unmatched = unmatchedRules
    if (unmatched.nonEmpty) {
      out(s"[verify] UNMATCHED RULES (${plural(unmatched.size, "rule")} matched zero methods):")
      unmatched.foreach(r => out(s"[verify]   ${formatUnmatchedRuleEntry(r)}"))
      out("")
    }

    out(s"[verify] Summary: ${plural(classesScanned, "class")} scanned, ${plural(excluded.size, "method")} excluded, ${plural(rescued.size, "method")} rescued")
  }

  /** Format the report as a string in the specified format: txt, json, or csv.
    *
    * @throws IllegalArgumentException if format is not one of: txt, json, csv
    */
  def formatReport(format: String): String = format.toLowerCase match {
    case "txt"  => formatTxt()
    case "json" => formatJson()
    case "csv"  => formatCsv()
    case other  => throw new IllegalArgumentException(s"Unknown report format: '$other'. Supported: txt, json, csv")
  }

  private def formatTxt(): String = {
    val excluded = excludedMethods
    val rescued  = rescuedMethods
    val lines    = scala.collection.mutable.ArrayBuffer.empty[String]
    if (excluded.nonEmpty) {
      lines += s"EXCLUDED (${plural(excluded.size, "method")}):"
      excluded.groupBy(_.fqcn).toSeq.sortBy(_._1).foreach { case (fqcn, methods) =>
        lines += s"  $fqcn"
        methods.sortBy(m => (m.methodName, m.descriptor)).foreach { m =>
          val ruleIdStr = if (m.exclusionIds.nonEmpty) s"  rule-id:${m.exclusionIds.mkString(",")}" else ""
          lines += s"    #${m.methodName}${m.descriptor}$ruleIdStr"
        }
      }
      lines += ""
    }
    if (rescued.nonEmpty) {
      lines += s"RESCUED by include rules (${plural(rescued.size, "method")}):"
      rescued.groupBy(_.fqcn).toSeq.sortBy(_._1).foreach { case (fqcn, methods) =>
        lines += s"  $fqcn"
        methods.sortBy(m => (m.methodName, m.descriptor)).foreach { m =>
          val exclStr = if (m.exclusionIds.nonEmpty) m.exclusionIds.mkString(",") else "(no-id)"
          val inclStr = if (m.inclusionIds.nonEmpty) m.inclusionIds.mkString(",") else "(no-id)"
          lines += s"    #${m.methodName}${m.descriptor}  excl:$exclStr \u2192 incl:$inclStr"
        }
      }
      lines += ""
    }
    val unmatched = unmatchedRules
    if (unmatched.nonEmpty) {
      lines += s"UNMATCHED RULES (${plural(unmatched.size, "rule")} matched zero methods):"
      unmatched.foreach(r => lines += s"  ${formatUnmatchedRuleEntry(r)}")
      lines += ""
    }
    lines += s"Summary: ${plural(classesScanned, "class")} scanned, ${plural(excluded.size, "method")} excluded, ${plural(rescued.size, "method")} rescued"
    lines.mkString("\n")
  }

  private def formatJson(): String = {
    def esc(s: String): String = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
      .replace("\b", "\\b")
      .replace("\f", "\\f")
    def str(s: String): String = s""""${esc(s)}""""
    def strArr(seq: Seq[String]): String = seq.map(str).mkString("[", ", ", "]")

    val excluded = excludedMethods.sortBy(m => (m.fqcn, m.methodName, m.descriptor))
    val rescued  = rescuedMethods.sortBy(m => (m.fqcn, m.methodName, m.descriptor))

    def excludedEntry(m: MatchedMethod): String =
      s"""    {"class": ${str(m.fqcn)}, "method": ${str(m.methodName)}, "descriptor": ${str(m.descriptor)}, "exclusionRuleIds": ${strArr(m.exclusionIds)}}"""

    def rescuedEntry(m: MatchedMethod): String =
      s"""    {"class": ${str(m.fqcn)}, "method": ${str(m.methodName)}, "descriptor": ${str(m.descriptor)}, "exclusionRuleIds": ${strArr(m.exclusionIds)}, "inclusionRuleIds": ${strArr(m.inclusionIds)}}"""

    def unmatchedEntry(r: MethodRule): String = {
      val pattern = if (r.rawText.nonEmpty) r.rawText else ""
      val idVal = r.id.map(str).getOrElse("\"\"")
      val sourceVal = str(r.source match {
        case GlobalSource(origin)               => s"global: $origin"
        case LocalSource(path) if path.nonEmpty => s"local: $path"
        case _                                  => ""
      })
      s"""    {"pattern": ${str(pattern)}, "id": $idVal, "source": $sourceVal}"""
    }

    val excBlock  = if (excluded.isEmpty) "[]" else s"[\n${excluded.map(excludedEntry).mkString(",\n")}\n  ]"
    val resBlock  = if (rescued.isEmpty)  "[]" else s"[\n${rescued.map(rescuedEntry).mkString(",\n")}\n  ]"
    val unmBlock  = if (unmatchedRules.isEmpty) "[]" else s"[\n${unmatchedRules.map(unmatchedEntry).mkString(",\n")}\n  ]"

    s"""{
  "classesScanned": $classesScanned,
  "excluded": $excBlock,
  "rescued": $resBlock,
  "unmatchedRules": $unmBlock
}"""
  }

  private def formatCsv(): String = {
    def cell(s: String): String =
      if (s.exists(c => c == ',' || c == '"' || c == '\n')) s""""${s.replace("\"", "\"\"")}"""" else s

    val excluded = excludedMethods.sortBy(m => (m.fqcn, m.methodName, m.descriptor))
    val rescued  = rescuedMethods.sortBy(m => (m.fqcn, m.methodName, m.descriptor))
    val sb       = new StringBuilder
    sb.append("outcome,class,method,descriptor,exclusionRuleIds,inclusionRuleIds\n")
    excluded.foreach { m =>
      sb.append(s"EXCLUDED,${cell(m.fqcn)},${cell(m.methodName)},${cell(m.descriptor)},${cell(m.exclusionIds.mkString("|"))},\n")
    }
    rescued.foreach { m =>
      sb.append(s"RESCUED,${cell(m.fqcn)},${cell(m.methodName)},${cell(m.descriptor)},${cell(m.exclusionIds.mkString("|"))},${cell(m.inclusionIds.mkString("|"))}\n")
    }
    unmatchedRules.foreach { r =>
      val pattern = if (r.rawText.nonEmpty) r.rawText else ""
      val id      = r.id.getOrElse("")
      sb.append(s"UNMATCHED_RULE,${cell(pattern)},,,${cell(id)},\n")
    }
    sb.toString()
  }
}

object VerifyScanner {
  def scan(classesDir: Path, rules: Seq[MethodRule]): ScanResult = {
    var classesScanned = 0
    val matchedMethods = mutable.ListBuffer.empty[MatchedMethod]
    // Track every rule that matched at least one method during the scan.
    // Uses reference identity via case-class equals (Pattern fields use reference equals).
    val matchedRuleSet = mutable.HashSet.empty[MethodRule]

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

            // Track every rule that matched this method (regardless of outcome).
            matchedRuleSet ++= resolution.exclusions
            matchedRuleSet ++= resolution.inclusions

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

    // Rules that never produced a match and are not marked forward-compat.
    val unmatchedRules = rules.filterNot(r => matchedRuleSet.contains(r) || r.forwardCompat)

    ScanResult(classesScanned, matchedMethods.size, matchedMethods.toSeq, unmatchedRules)
  }
}
