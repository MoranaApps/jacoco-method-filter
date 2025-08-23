package io.moranaapps.jacocomethodfilter

import java.nio.file.{Files, Path}
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._
import org.objectweb.asm.Opcodes

final case class MethodRule(
                             cls: Pattern,
                             method: Pattern,
                             desc: Pattern,
                             flags: Set[String]      // public|protected|private|synthetic|bridge
                           )

object Rules {

  def load(path: Path): Seq[MethodRule] = {
    if (!Files.exists(path)) return Seq.empty
    val lines = Files.readAllLines(path).asScala.toVector
    lines.flatMap(parseLine)
  }

  private def parseLine(raw: String): Option[MethodRule] = {
    val line = raw.trim
    if (line.isEmpty || line.startsWith("#")) return None

    val parts = line.split("\\s+", 2)
    val main = parts(0)
    val flags = if (parts.length > 1)
      parts(1).replace(",", " ").split("\\s+").map(_.trim).filter(_.nonEmpty).toSet
    else Set.empty[String]

    require(main.contains("#") && main.contains("(") && main.contains(")"),
      s"Invalid rule (expected <FQCN>#<method>(<desc>)): $line")

    val Array(clsGlob, rest) = main.split("#", 2)
    val idx = rest.indexOf('(')
    require(idx >= 0, s"Missing '(' in descriptor: $line")

    val methodGlob = rest.substring(0, idx)
    val descGlob   = rest.substring(idx) // includes '('

    Some(MethodRule(
      Glob.toRegex(clsGlob),
      Glob.toRegex(methodGlob),
      Glob.toRegex(descGlob),
      flags
    ))
  }

  def matches(
               r: MethodRule,
               fqcnDots: String,
               methodName: String,
               desc: String,
               access: Int,
             ): Boolean = {
    val fqcnSlashes = fqcnDots.replace('.', '/')

    val clsOk    = r.cls.matcher(fqcnDots).matches() || r.cls.matcher(fqcnSlashes).matches()
    val methodOk = r.method.matcher(methodName).matches()
    val descOk   = r.desc.matcher(desc).matches()

    val isPublic    = (access & Opcodes.ACC_PUBLIC)    != 0
    val isProtected = (access & Opcodes.ACC_PROTECTED) != 0
    val isPrivate   = (access & Opcodes.ACC_PRIVATE)   != 0
    val isSynthetic = (access & Opcodes.ACC_SYNTHETIC) != 0
    val isBridge    = (access & Opcodes.ACC_BRIDGE)    != 0

    val flagsOk =
      (!r.flags.contains("public")    || isPublic)    &&
        (!r.flags.contains("protected") || isProtected) &&
        (!r.flags.contains("private")   || isPrivate)   &&
        (!r.flags.contains("synthetic") || isSynthetic) &&
        (!r.flags.contains("bridge")    || isBridge)

    clsOk && methodOk && descOk && flagsOk
  }
}
