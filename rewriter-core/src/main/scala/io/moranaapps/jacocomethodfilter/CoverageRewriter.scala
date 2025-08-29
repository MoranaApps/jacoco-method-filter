package io.moranaapps.jacocomethodfilter

import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import org.objectweb.asm._
import org.objectweb.asm.Opcodes._
import scopt.OParser

/** CLI:
  *   --in   <classesDir>
  *   --out  <classesFilteredDir>
  *   --rules <rulesFile>
  *   [--dry-run] [--ann <fqcn>] [--visible] [--verbose]
  *
  * Rules line format (blank lines & # comments ignored):
  *   FQCN_glob#method_glob(descriptor_glob) [flags/predicates...]
  * Flags: public|protected|private|static|abstract|synthetic|bridge
  * Predicates: ret:<glob> name-contains:<s> name-starts:<s> name-ends:<s> id:<tag>
  */
object CoverageRewriter {

  final case class Config(
    in: Path = Paths.get("."),
    out: Path = Paths.get("./classes-filtered"),
    rules: Path = Paths.get("./rules/coverage-rules.txt"),
    dryRun: Boolean = false,
    annFqcn: String = "io.moranaapps.jmf.GeneratedByJacocoMethodFilter",
    visible: Boolean = false,   // true = RuntimeVisible, false = RuntimeInvisible
    verbose: Boolean = false
  )

  // ----- CLI parsing (scopt) -----
  private val builder = OParser.builder[Config]
  private val parser = {
    import builder._
    OParser.sequence(
      programName("jacoco-method-filter"),
      head("jacoco-method-filter", "0.1.4"),
      opt[File]("in").required().valueName("<dir>").action((f, c) => c.copy(in = f.toPath))
        .validate(f => if (f.exists() && f.isDirectory) success else failure("--in must be an existing directory")),
      opt[File]("out").required().valueName("<dir>").action((f, c) => c.copy(out = f.toPath)),
      opt[File]("rules").required().valueName("<file>").action((f, c) => c.copy(rules = f.toPath))
        .validate(f => if (f.exists()) success else failure("--rules file not found")),
      opt[Unit]("dry-run").action((_, c) => c.copy(dryRun = true)).text("log matches but don't write .class files"),
      opt[String]("ann").valueName("<fqcn>").action((s, c) => c.copy(annFqcn = s))
        .text("override annotation FQCN (simple name must contain 'Generated')"),
      opt[Unit]("visible").action((_, c) => c.copy(visible = true))
        .text("mark annotation as RuntimeVisible (default is RuntimeInvisible/CLASS)"),
      opt[Unit]("verbose").abbr("v").action((_, c) => c.copy(verbose = true)),
      help("help").text("prints this usage text")
    )
  }

  def main(args: Array[String]): Unit = {
    OParser.parse(parser, args, Config()) match {
      case Some(cfg) =>
        run(cfg)
      case None =>
        sys.exit(2)
    }
  }

  // ----- Rules model -----

  final case class Rule(
    cls: Glob,              // FQCN dot-form glob: com.example.*$Inner
    method: Glob,           // method name glob
    desc: Glob,             // descriptor glob  e.g. (I)I or (*)*
    flags: Set[Flag],       // access flags required (all must match)
    pred: Predicates,       // extra predicates
    raw: String             // original line for logs
  )

  sealed trait Flag
  object Flag {
    case object Public    extends Flag
    case object Protected extends Flag
    case object Private   extends Flag
    case object Static    extends Flag
    case object Abstract  extends Flag
    case object Synthetic extends Flag
    case object Bridge    extends Flag

    def fromString(s: String): Option[Flag] = s.toLowerCase match {
      case "public"    => Some(Public)
      case "protected" => Some(Protected)
      case "private"   => Some(Private)
      case "static"    => Some(Static)
      case "abstract"  => Some(Abstract)
      case "synthetic" => Some(Synthetic)
      case "bridge"    => Some(Bridge)
      case _           => None
    }
  }

  final case class Predicates(
    ret: Option[Glob] = None,
    nameContains: Option[String] = None,
    nameStarts: Option[String] = None,
    nameEnds: Option[String] = None,
    id: Option[String] = None
  )

  /** Simple glob → regex (dot form for class names; JVM descriptor for desc/ret) */
  final case class Glob private (regex: String) {
    private val r = regex.r
    def matches(s: String): Boolean = r.pattern.matcher(s).matches()
  }
  object Glob {
    def apply(glob: String, isDescriptor: Boolean = false): Glob = {
      // escape regex chars, then expand '*'
      val esc = java.util.regex.Pattern.quote(glob).replace("\\*", ".*")
      // for descriptors, keep slashes and parens as-is; for dot-form, ensure we match whole string
      val rx =
        if (isDescriptor) s"^$esc$$"
        else s"^$esc$$"
      Glob(rx)
    }
    val AnyDesc: Glob = Glob("(.*)", isDescriptor = true) // for omitted desc
  }

  private def parseRules(path: Path, verbose: Boolean): Seq[Rule] = {
    val lines =
      if (!Files.exists(path)) sys.error(s"[jmf] Rules file not found: $path")
      else Files.readAllLines(path).asScala.toVector

    lines.zipWithIndex.flatMap { case (raw0, idx) =>
      val raw = raw0.trim
      if (raw.isEmpty || raw.startsWith("#")) None
      else {
        // split trailing tokens (flags/preds)
        val (lhs, rhs) = raw.span(!_.isWhitespace)
        val toks = raw.drop(lhs.length).trim.split("[ ,]+").filter(_.nonEmpty).toList

        val hashIdx = lhs.indexOf('#')
        val parenIdx = lhs.indexOf('(')
        if (hashIdx <= 0 || parenIdx < 0 || !lhs.endsWith(")"))
          sys.error(s"[jmf] Bad rule at ${path.getFileName}:${idx + 1}: $raw")

        val clsGlob = lhs.substring(0, hashIdx) // dot-form
        val methGlob = lhs.substring(hashIdx + 1, parenIdx)
        val descGlob0 = lhs.substring(parenIdx)                     // e.g. "(I)V"
        val descGlob = if (descGlob0 == "()" || descGlob0 == "(*)") "(*)*" else descGlob0

        val (flags, preds) = parseFlagsAndPreds(toks)

        Some(
          Rule(
            cls  = Glob(clsGlob, isDescriptor = false),
            method = Glob(methGlob, isDescriptor = false),
            desc   = if (descGlob.nonEmpty) Glob(descGlob, isDescriptor = true) else Glob.AnyDesc,
            flags  = flags,
            pred   = preds,
            raw = raw
          )
        )
      }
    }
  }

  private def parseFlagsAndPreds(toks: List[String]): (Set[Flag], Predicates) = {
    var flags = Set.empty[Flag]
    var ret: Option[Glob] = None
    var nc: Option[String] = None
    var ns: Option[String] = None
    var ne: Option[String] = None
    var id: Option[String] = None

    toks.foreach { t =>
      Flag.fromString(t) match {
        case Some(f) => flags += f
        case None =>
          if (t.startsWith("ret:")) ret = Some(Glob(t.stripPrefix("ret:"), isDescriptor = true))
          else if (t.startsWith("name-contains:")) nc = Some(t.stripPrefix("name-contains:"))
          else if (t.startsWith("name-starts:"))   ns = Some(t.stripPrefix("name-starts:"))
          else if (t.startsWith("name-ends:"))     ne = Some(t.stripPrefix("name-ends:"))
          else if (t.startsWith("id:"))            id = Some(t.stripPrefix("id:"))
      }
    }
    (flags, Predicates(ret, nc, ns, ne, id))
  }

  // ----- Rewriter -----

  private def run(cfg: Config): Unit = {
    val annDesc = "L" + cfg.annFqcn.replace('.', '/') + ";"
    if (cfg.verbose) {
      println(s"[jmf] in      = ${cfg.in.toAbsolutePath}")
      println(s"[jmf] out     = ${cfg.out.toAbsolutePath}")
      println(s"[jmf] rules   = ${cfg.rules.toAbsolutePath}")
      println(s"[jmf] ann     = ${cfg.annFqcn} (desc: $annDesc, visible=${cfg.visible})")
      println(s"[jmf] dryRun  = ${cfg.dryRun}")
    }

    val rules = parseRules(cfg.rules, cfg.verbose)
    if (rules.isEmpty) {
      println(s"[jmf] No rules found in ${cfg.rules} — nothing to do.")
      if (!cfg.dryRun) mirrorTree(cfg.in, cfg.out) // still mirror if you want full pipeline
      return
    }
    if (!Files.exists(cfg.out)) Files.createDirectories(cfg.out)

    val classFiles = Files.walk(cfg.in).iterator().asScala.filter(p => p.toString.endsWith(".class")).toVector
    var matchedMethods = 0
    var rewrittenFiles = 0

    classFiles.foreach { p =>
      val rel = cfg.in.relativize(p)
      val target = cfg.out.resolve(rel)
      val parent = target.getParent
      if (!Files.exists(parent)) Files.createDirectories(parent)

      val bytes = Files.readAllBytes(p)
      val cr = new ClassReader(bytes)
      val cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS) // annotation only; frames unchanged
      val classDotNameRef = new Array

      val cv = new ClassVisitor(ASM9, cw) {
        private var classDot: String = _
        override def visit(version: Int, access: Int, name: String, sig: String, superName: String, ifaces: Array[String]): Unit = {
          classDot = name.replace('/', '.')
          classDotNameRef(0) = classDot
          super.visit(version, access, name, sig, superName, ifaces)
        }
        override def visitMethod(access: Int, name: String, desc: String, sig: String, exceptions: Array[String]): MethodVisitor = {
          val mv = super.visitMethod(access, name, desc, sig, exceptions)
          val matched = matchesAnyRule(rules, classDot, name, desc, access)
          if (matched.nonEmpty) {
            matchedMethods += 1
            if (cfg.verbose || cfg.dryRun) {
              val ids = matched.flatMap(_.pred.id).distinct.mkString(",")
              val tag = if (ids.nonEmpty) s" [rules:$ids]" else ""
              println(s"[jmf] match: $classDot#$name$desc$tag")
            }
            if (!cfg.dryRun) {
              // Inject annotation on the method
              val av = mv.visitAnnotation(annDesc, cfg.visible) // visible=true => RuntimeVisible
              if (av != null) av.visitEnd()
            }
          }
          mv
        }
      }

      cr.accept(cv, 0)
      val wrote = if (cfg.dryRun) false else {
        val arr = cw.toByteArray
        if (!java.util.Arrays.equals(arr, bytes)) {
          Files.write(target, arr); true
        } else {
          // no change (no matched methods) — mirror original bytes
          mirrorFile(bytes, target); false
        }
      }

      if (wrote) rewrittenFiles += 1
      if (cfg.dryRun && !Files.exists(target.getParent)) Files.createDirectories(target.getParent)
      if (cfg.dryRun) mirrorFile(bytes, target) // mirror original for full pipeline continuity
    }

    println(s"[jmf] matched methods: $matchedMethods")
    if (!cfg.dryRun) println(s"[jmf] rewritten class files: $rewrittenFiles")
  }

  private def mirrorTree(in: Path, out: Path): Unit = {
    Files.walk(in).iterator().asScala.foreach { p =>
      val rel = in.relativize(p)
      val t = out.resolve(rel)
      if (Files.isDirectory(p)) Files.createDirectories(t)
      else if (p.toString.endsWith(".class")) Files.write(t, Files.readAllBytes(p))
    }
  }
  private def mirrorFile(bytes: Array[Byte], target: Path): Unit = {
    if (!Files.exists(target.getParent)) Files.createDirectories(target.getParent)
    Files.write(target, bytes)
  }

  private def matchesAnyRule(rules: Seq[Rule], clsDot: String, meth: String, desc: String, access: Int): Seq[Rule] = {
    rules.filter { r =>
      r.cls.matches(clsDot) &&
      r.method.matches(meth) &&
      (r.desc.matches(desc) || r.desc == Glob.AnyDesc) &&
      flagsMatch(r.flags, access) &&
      predsMatch(r.pred, meth, desc)
    }
  }

  private def flagsMatch(flags: Set[Flag], access: Int): Boolean = {
    flags.forall {
      case Flag.Public    => (access & ACC_PUBLIC) != 0
      case Flag.Protected => (access & ACC_PROTECTED) != 0
      case Flag.Private   => (access & ACC_PRIVATE) != 0
      case Flag.Static    => (access & ACC_STATIC) != 0
      case Flag.Abstract  => (access & ACC_ABSTRACT) != 0
      case Flag.Synthetic => (access & ACC_SYNTHETIC) != 0
      case Flag.Bridge    => (access & ACC_BRIDGE) != 0
    }
  }

  private def predsMatch(p: Predicates, name: String, desc: String): Boolean = {
    val retDesc = desc
