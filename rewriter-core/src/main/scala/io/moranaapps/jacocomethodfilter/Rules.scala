package io.moranaapps.jacocomethodfilter

import java.nio.file.{Files, Path}
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._
import org.objectweb.asm.Opcodes

// --- Selector helpers -------------------------------------------------------

private object Selectors {
  private val meta = "\\.^$+{}[]()|"

  /** Convert a glob to a compiled regex Pattern. */
  def globToRegex(glob: String): Pattern = {
    val sb = new StringBuilder("^")
    glob.foreach {
      case '*' => sb.append(".*")
      case '?' => sb.append(".")
      case c if meta.indexOf(c) >= 0 => sb.append("\\").append(c)
      case c => sb.append(c)
    }
    sb.append("$")
    Pattern.compile(sb.toString)
  }
}

// --- Rule model -------------------------------------------------------------

final case class MethodRule(
                             cls: Pattern,                 // class selector (glob)
                             method: Pattern,              // method selector (glob)
                             desc: Pattern,                // full descriptor selector "(args)ret" (glob)
                             flags: Set[String],           // public|protected|private|synthetic|bridge (space-separated)
                             // Predicates:
                             retGlob: Option[Pattern],     // ret:<glob> matches only the return type
                             id: Option[String],           // id:<string> for logs/reports
                             nameContains: Option[String], // name-contains:<s>
                             nameStarts: Option[String],   // name-starts:<s>
                             nameEnds: Option[String],     // name-ends:<s>
                           )

object Rules {

  // Normalize short/omitted descriptors.
  //  - ""  or "()"  -> "(*)*"
  //  - "(*)"        -> "(*)*"
  private def normalizeDesc(descSel0: String): String = {
    if (descSel0 == null || descSel0.isEmpty || descSel0 == "()") "(*)*"
    else if (descSel0 == "(*)") "(*)*"
    else descSel0
  }

  def load(path: Path): Seq[MethodRule] = {
    if (!Files.exists(path)) return Seq.empty
    val lines = Files.readAllLines(path).asScala.toVector
    lines.flatMap(parseLine)
  }

  private def parseLine(raw: String): Option[MethodRule] = {
    val line = raw.trim
    if (line.isEmpty || line.startsWith("#")) return None

    // Split into <main> and the trailing tokens (flags/predicates)
    val firstWs = line.indexWhere(_.isWhitespace)
    val (main, restTokens) =
      if (firstWs < 0) (line, "")
      else (line.substring(0, firstWs), line.substring(firstWs).trim)

    val Array(clsSel, rest) = main.split("#", 2)
    require(rest.nonEmpty, s"Missing method name after '#': $line")

    val idx = rest.indexOf('(')

    val (methodSel, descSel0) =
      if (idx >= 0) {
        // has explicit descriptor
        (rest.substring(0, idx), rest.substring(idx)) // includes '('
      } else {
        // no descriptor provided -> treat as wildcard
        (rest, "(*)")
      }

    val descSel = normalizeDesc(descSel0)

    // Parse flags + predicates (space- or comma-separated)
    var flags        = Set.empty[String]
    var retGlob      = Option.empty[Pattern]
    var id           = Option.empty[String]
    var nameContains = Option.empty[String]
    var nameStarts   = Option.empty[String]
    var nameEnds     = Option.empty[String]

    restTokens.replace(",", " ").split("\\s+").filter(_.nonEmpty).foreach {
      case t @ ("public" | "protected" | "private" | "synthetic" | "bridge" | "static" | "abstract") =>
        flags += t
      case kv if kv.startsWith("ret:")            => retGlob      = Some(Selectors.globToRegex(kv.stripPrefix("ret:")))
      case kv if kv.startsWith("id:")             => id           = Some(kv.stripPrefix("id:"))
      case kv if kv.startsWith("name-contains:")  => nameContains = Some(kv.stripPrefix("name-contains:"))
      case kv if kv.startsWith("name-starts:")    => nameStarts   = Some(kv.stripPrefix("name-starts:"))
      case kv if kv.startsWith("name-ends:")      => nameEnds     = Some(kv.stripPrefix("name-ends:"))
      case _ => () // ignore unknown tokens for forward-compat
    }

    // Reject regex selectors explicitly to keep DSL simple
    def ensureNoRegex(token: String, what: String): Unit = {
      require(
        !token.startsWith("re:"),
        s"Regex selectors are not supported for $what. Use glob syntax instead. Got: $token"
      )
    }

    ensureNoRegex(clsSel,    "class")
    ensureNoRegex(methodSel, "method")
    ensureNoRegex(descSel,   "descriptor")

    Some(MethodRule(
      cls          = Selectors.globToRegex(clsSel),
      method       = Selectors.globToRegex(methodSel),
      desc         = Selectors.globToRegex(descSel),
      flags        = flags,
      retGlob      = retGlob,     // note: still a glob
      id           = id,
      nameContains = nameContains,
      nameStarts   = nameStarts,
      nameEnds     = nameEnds
    ))
  }

  def matches(
               r: MethodRule,
               fqcn: String, // e.g., "za.co.absa.Foo$Bar"
               methodName: String, // e.g., "copy", "$anonfun$...", "contextSearch"
               desc: String, // full JVM method descriptor: "(args...)ret"
               access: Int,
             ): Boolean = {
    require(!fqcn.contains('/'),
      s"Pass FQCN in dot form (e.g., com.example.Foo). Got: $fqcn")

    val fqcnSlashes = fqcn.replace('.', '/')

    // Class match: allow both dot and slash forms
    val clsOk =
      r.cls.matcher(fqcn).matches() ||
        r.cls.matcher(fqcnSlashes).matches()

    // Method name match + helpers
    val nameHelpersOk =
      r.nameContains.forall(methodName.contains) &&
        r.nameStarts.forall(methodName.startsWith) &&
        r.nameEnds.forall(methodName.endsWith)

    val methodOk = r.method.matcher(methodName).matches() && nameHelpersOk

    // Descriptor match (whole "(args)ret")
    val descOk = r.desc.matcher(desc).matches()

    // Flags
    val isPublic    = (access & Opcodes.ACC_PUBLIC)    != 0
    val isProtected = (access & Opcodes.ACC_PROTECTED) != 0
    val isPrivate   = (access & Opcodes.ACC_PRIVATE)   != 0
    val isSynthetic = (access & Opcodes.ACC_SYNTHETIC) != 0
    val isBridge    = (access & Opcodes.ACC_BRIDGE)    != 0
    val isStatic    = (access & Opcodes.ACC_STATIC)    != 0
    val isAbstract  = (access & Opcodes.ACC_ABSTRACT)  != 0

    val flagsOk =
      (!r.flags.contains("public")    || isPublic)    &&
        (!r.flags.contains("protected") || isProtected) &&
        (!r.flags.contains("private")   || isPrivate)   &&
        (!r.flags.contains("synthetic") || isSynthetic) &&
        (!r.flags.contains("bridge")    || isBridge)    &&
        (!r.flags.contains("static")    || isStatic)    &&
        (!r.flags.contains("abstract")  || isAbstract)

    // Return predicate: ret:<glob> matches only the return part
    val parenEnd = desc.indexOf(')')
    val retType  = if (parenEnd >= 0 && parenEnd + 1 < desc.length) desc.substring(parenEnd + 1) else ""
    val retOk    = r.retGlob.forall(_.matcher(retType).matches())

    clsOk && methodOk && descOk && flagsOk && retOk
  }
}
