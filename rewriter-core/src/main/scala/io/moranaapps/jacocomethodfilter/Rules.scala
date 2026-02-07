package io.moranaapps.jacocomethodfilter

import java.io.{BufferedReader, InputStreamReader}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.regex.Pattern
import scala.collection.JavaConverters._
import org.objectweb.asm.Opcodes

// --- Rule mode and source ---------------------------------------------------

sealed trait RuleMode
case object Exclude extends RuleMode
case object Include extends RuleMode

sealed trait RuleSource
final case class GlobalSource(origin: String) extends RuleSource
final case class LocalSource(path: String) extends RuleSource
final case class LegacySource(path: String) extends RuleSource

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
                             mode: RuleMode = Exclude,     // exclude or include
                             source: RuleSource = LegacySource("") // where this rule came from
                           )

object Rules {

  // HTTP timeout settings for loading rules from URLs
  private val UrlConnectTimeoutMs = 10000
  private val UrlReadTimeoutMs = 10000

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
    val source = LegacySource(path.toString)
    lines.flatMap(line => parseLine(line, source))
  }

  private[jacocomethodfilter] def parseLine(raw: String, source: RuleSource = LegacySource("")): Option[MethodRule] = {
    val line = raw.trim
    if (line.isEmpty || line.startsWith("#")) return None

    // Check for include mode (+ prefix)
    val (mode, lineWithoutPrefix) = if (line.startsWith("+")) {
      (Include, line.substring(1).trim)
    } else {
      (Exclude, line)
    }

    // Split into <main> and the trailing tokens (flags/predicates)
    val firstWs = lineWithoutPrefix.indexWhere(_.isWhitespace)
    val (main, restTokens) =
      if (firstWs < 0) (lineWithoutPrefix, "")
      else (lineWithoutPrefix.substring(0, firstWs), lineWithoutPrefix.substring(firstWs).trim)

    val Array(clsSel, rest) = main.split("#", 2)
    require(rest.nonEmpty, s"Missing method name after '#': $raw")

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
      nameEnds     = nameEnds,
      mode         = mode,
      source       = source
    ))
  }

  def matches(
               r: MethodRule,
               fqcn: String, // e.g., "za.co.absa.Foo$Bar"
               methodName: String, // e.g., "copy", "$anonfun$...", "contextSearch"
               desc: String, // full JVM method descriptor: "(args...)ret"
               access: Int
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

  /**
   * Load rules from a source that can be either a local path or an HTTP/HTTPS URL.
   * @param source path or URL
   * @param ruleSource metadata about where this rule came from
   * @return sequence of method rules
   */
  def loadFromSource(source: String, ruleSource: RuleSource): Seq[MethodRule] = {
    if (source.startsWith("http://") || source.startsWith("https://")) {
      loadFromUrl(source, ruleSource)
    } else {
      loadFromPath(Paths.get(source), ruleSource)
    }
  }

  private def loadFromUrl(urlStr: String, ruleSource: RuleSource): Seq[MethodRule] = {
    val url = new URL(urlStr)
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    try {
      conn.setRequestMethod("GET")
      conn.setConnectTimeout(UrlConnectTimeoutMs)
      conn.setReadTimeout(UrlReadTimeoutMs)
      
      val responseCode = conn.getResponseCode
      if (responseCode != 200) {
        throw new RuntimeException(s"Failed to fetch rules from $urlStr: HTTP $responseCode")
      }
      
      val reader = new BufferedReader(new InputStreamReader(conn.getInputStream, StandardCharsets.UTF_8))
      try {
        val lines = scala.collection.mutable.ListBuffer[String]()
        var line = reader.readLine()
        while (line != null) {
          lines += line
          line = reader.readLine()
        }
        lines.toVector.flatMap(line => parseLine(line, ruleSource))
      } finally {
        reader.close()
      }
    } finally {
      conn.disconnect()
    }
  }

  private def loadFromPath(path: Path, ruleSource: RuleSource): Seq[MethodRule] = {
    if (!Files.exists(path)) return Seq.empty
    val lines = Files.readAllLines(path).asScala.toVector
    lines.flatMap(line => parseLine(line, ruleSource))
  }

  /**
   * Load and merge rules from global, local, and legacy sources.
   * @param globalSource optional global rules (path or URL)
   * @param localPath optional local rules file
   * @param legacyPath optional legacy rules file (for backward compatibility)
   * @return merged sequence of all rules
   */
  def loadAll(globalSource: Option[String], localPath: Option[Path], legacyPath: Option[Path]): Seq[MethodRule] = {
    val globalRules = globalSource match {
      case Some(src) => loadFromSource(src, GlobalSource(src))
      case None => Seq.empty
    }
    
    val localRules = localPath match {
      case Some(path) => loadFromPath(path, LocalSource(path.toString))
      case None => Seq.empty
    }
    
    val legacyRules = legacyPath match {
      case Some(path) => load(path)
      case None => Seq.empty
    }
    
    globalRules ++ localRules ++ legacyRules
  }
}

// --- Rule Resolution --------------------------------------------------------

final case class Resolution(exclusions: Seq[MethodRule], inclusions: Seq[MethodRule]) {
  def shouldExclude: Boolean = exclusions.nonEmpty && inclusions.isEmpty
  def isRescued: Boolean = exclusions.nonEmpty && inclusions.nonEmpty
}

object RuleResolver {
  def resolve(rules: Seq[MethodRule], fqcn: String, methodName: String, desc: String, access: Int): Resolution = {
    val matchingRules = rules.filter(r => Rules.matches(r, fqcn, methodName, desc, access))
    val exclusions = matchingRules.filter(_.mode == Exclude)
    val inclusions = matchingRules.filter(_.mode == Include)
    Resolution(exclusions, inclusions)
  }
}
