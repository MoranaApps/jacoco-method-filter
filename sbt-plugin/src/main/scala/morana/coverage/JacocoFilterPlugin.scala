package morana.coverage

import morana.coverage.Keys.autoImport._
import sbt._
import sbt.Keys._

/**
 * JacocoFilterPlugin
 * -----------------
 * A bundled sbt plugin version of the integration snippets under `integration/sbt/`.
 */
object JacocoFilterPlugin extends AutoPlugin {

  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = noTrigger

  // ---- helper: all aggregated descendants (BFS), excluding the root itself
  private def aggregatedDescendants(e: Extracted, root: ProjectRef): Vector[ProjectRef] = {
    val s     = e.structure
    val seen  = scala.collection.mutable.LinkedHashSet[ProjectRef](root)
    val queue = scala.collection.mutable.Queue[ProjectRef](root)
    while (queue.nonEmpty) {
      val ref  = queue.dequeue()
      val kids = Project.getProject(ref, s).toList.flatMap(_.aggregate)
      kids.foreach { k =>
        if (!seen(k)) {
          seen += k
          queue.enqueue(k)
        }
      }
    }
    seen.toVector.tail // drop root
  }

  // ---- helper: only those that set jacocoPluginEnabled := true
  private def enabledUnder(state: State): Vector[ProjectRef] = {
    val e    = Project.extract(state)
    val here = e.currentRef
    val all  = aggregatedDescendants(e, here) // children only (no root)
    all.filter { ref =>
      e.getOpt((ref / jacocoPluginEnabled): SettingKey[Boolean]).getOrElse(false)
    }
  }

  // ---- commands
  private lazy val jacocoCleanAllCmd = Command.command("jacocoCleanAll") { state =>
    val targets = enabledUnder(state)
    if (targets.isEmpty) {
      println("[jacoco] nothing to clean (no enabled modules under this aggregate).")
      state
    } else {
      targets.foldLeft(state) { (st, ref) => Command.process(s"${ref.project}/jacocoClean", st) }
    }
  }

  private lazy val jacocoReportAllCmd = Command.command("jacocoReportAll") { state =>
    val e       = Project.extract(state)
    val current = e.currentRef
    val under   = enabledUnder(state)

    val selfEnabled = e.getOpt(current / jacocoPluginEnabled).getOrElse(false)
    val targets     = (if (selfEnabled) current +: under else under).distinct

    if (targets.isEmpty) {
      println("[jacoco] nothing to report (no enabled modules here).")
      state
    } else {
      targets.foldLeft(state) { (st, ref) =>
        Command.process(s"${ref.project}/jacocoReport", st)
      }
    }
  }

  private def agentJar(cp: Seq[Attributed[File]]): File = {
    val files = cp.map(_.data)
    files
      .find(f => f.getName.startsWith("org.jacoco.agent-") && f.getName.contains("-runtime"))
      .orElse(files.find(f => f.getName.contains("jacoco") && f.getName.contains("agent") && f.getName.contains("runtime")))
      .orElse(files.find(f => f.getName.startsWith("org.jacoco.agent-") && f.getName.endsWith(".jar")))
      .getOrElse(sys.error("JaCoCo runtime agent JAR not found on Test / dependencyClasspath"))
  }

  private def cliJar(cp: Seq[Attributed[File]]): File = {
    val files = cp.map(_.data)
    files
      .find(f => f.getName.startsWith("org.jacoco.cli-") && f.getName.contains("nodeps"))
      .orElse(files.find(_.getName.startsWith("org.jacoco.cli-")))
      .getOrElse(sys.error("org.jacoco.cli (nodeps) JAR not found on Test / dependencyClasspath"))
  }

  private val defaultIncludes = Seq("**")
  private val defaultExcludes = Seq("scala.*", "java.*", "sun.*", "jdk.*")

  override def buildSettings: Seq[Def.Setting[_]] = Seq(
    jacocoPluginEnabled := false,
    commands ++= Seq(jacocoCleanAllCmd, jacocoReportAllCmd)
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    jacocoPluginEnabled := false,

    // ---- defaults + coordinates
    jacocoVersion := "0.8.12",
    jmfCoreVersion := "1.0.0",
    libraryDependencies ++= Seq(
      ("org.jacoco" % "org.jacoco.agent" % jacocoVersion.value % Test).classifier("runtime"),
      ("org.jacoco" % "org.jacoco.cli" % jacocoVersion.value % Test).classifier("nodeps"),
      "io.github.moranaapps" %% "jacoco-method-filter-core" % jmfCoreVersion.value % Jmf.name
    ),

    jacocoSetUserDirToBuildRoot := true,

    jacocoExecFile := target.value / "jacoco" / "jacoco.exec",
    jacocoReportDir := target.value / "jacoco" / "report",
    jacocoIncludes := defaultIncludes,
    jacocoExcludes := defaultExcludes,
    jacocoAppend := false,
    jacocoFailOnMissingExec := false,

    jacocoReportName := {
      val moduleId = thisProject.value.id
      s"Report: $moduleId - scala:${scalaVersion.value}"
    },

    // --- JMF tool wiring
    ivyConfigurations += Jmf,

    jmfOutDir := target.value / "jmf",
    jmfRulesFile := (ThisBuild / baseDirectory).value / "jmf-rules.txt",
    jmfCliMain := "io.moranaapps.jacocomethodfilter.CoverageRewriter",
    jmfDryRun := false,
    jmfEnabled := true,
    jmfInitRulesForce := false,
    jmfRulesTemplate := "scala",

    jmfInitRules := {
      val log = streams.value.log
      val rulesFile = jmfRulesFile.value
      val force = jmfInitRulesForce.value
      val template = jmfRulesTemplate.value
      
      if (rulesFile.exists() && !force) {
        log.info(s"[jmf] Rules file already exists: ${rulesFile.getAbsolutePath}")
        log.info("[jmf] To overwrite, set jmfInitRulesForce := true and run again.")
        rulesFile
      } else {
        val templateContent = template.toLowerCase match {
          case "scala-java" | "scalajava" | "java" => DefaultRulesTemplates.scalaJava
          case _ => DefaultRulesTemplates.scala
        }
        
        IO.write(rulesFile, templateContent)
        log.info(s"[jmf] Created rules file: ${rulesFile.getAbsolutePath}")
        log.info("[jmf] Next steps:")
        log.info("[jmf]   1. Review and customize the rules for your project")
        log.info("[jmf]   2. Run 'sbt jacocoOn test jacocoReportAll jacocoOff' to generate coverage")
        rulesFile
      }
    },

    jmfRewrite := {
      val _ = (Compile / compile).value

      val rules     = jmfRulesFile.value
      val log       = streams.value.log
      val workDir   = baseDirectory.value
      val classesIn = (Compile / classDirectory).value
      val enabled   = jacocoPluginEnabled.value

      val compileCp: Seq[File] = Attributed.data((Compile / fullClasspath).value)
      val jmfJars: Seq[File] = (Jmf / update).value.matching(artifactFilter(`type` = "jar")).distinct
      val cp: Seq[File] = (compileCp ++ jmfJars :+ (Compile / classDirectory).value).distinct
      val cpStr = cp.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

      val javaBin = {
        val h = sys.props.get("java.home").getOrElse("")
        if (h.nonEmpty) new java.io.File(new java.io.File(h, "bin"), "java").getAbsolutePath else "java"
      }

      if (!enabled) classesIn
      else if (!classesIn.exists) {
        log.warn(s"[jmf] compiled classes dir not found, skipping: ${classesIn.getAbsolutePath}")
        classesIn
      } else {
        val hasClasses = (classesIn ** sbt.GlobFilter("*.class")).get.nonEmpty
        if (!hasClasses) {
          log.warn(s"[jmf] no .class files under ${classesIn.getAbsolutePath}; skipping.")
          classesIn
        } else if (!rules.exists) {
          log.warn(s"[jmf] rules file missing: ${rules.getAbsolutePath}; skipping.")
          classesIn
        } else {
          val outDir = jmfOutDir.value / "classes-filtered"
          IO.delete(outDir)
          IO.createDirectory(outDir)

          val args = Seq(
            javaBin,
            "-cp",
            cpStr,
            jmfCliMain.value,
            "--in",
            classesIn.getAbsolutePath,
            "--out",
            outDir.getAbsolutePath,
            "--rules",
            rules.getAbsolutePath
          ) ++ (if (jmfDryRun.value) Seq("--dry-run") else Seq())

          log.info(s"[jmf] rewrite: ${args.mkString(" ")}")
          val code = scala.sys.process.Process(args, workDir).!
          if (code != 0) sys.error(s"[jmf] rewriter failed ($code)")
          outDir
        }
      }
    },

    jmfPrepareForTests := Def.taskDyn {
      if (jmfEnabled.value) Def.task { jmfRewrite.value; () }
      else Def.task { () }
    }.value,

    // Ensure tests see rewritten main classes first (when enabled)
    Test / fullClasspath := Def.taskDyn {
      val testOut   = (Test / classDirectory).value
      val mainOut   = (Compile / classDirectory).value
      val deps      = (Test / internalDependencyClasspath).value
      val ext       = (Test / externalDependencyClasspath).value
      val unmanaged = (Test / unmanagedClasspath).value
      val scalaJars = (Test / scalaInstance).value.allJars.map(Attributed.blank(_)).toVector
      val resources = (Test / resourceDirectories).value.map(Attributed.blank)

      def build(rewrittenOpt: Option[File]) = Def.task {
        val rewrittenDifferent = rewrittenOpt.filter(_ != mainOut)
        val prefix = rewrittenDifferent.toVector.map(Attributed.blank) :+ Attributed.blank(testOut)
        val rest = (deps ++ ext ++ scalaJars ++ resources ++ unmanaged)
          .filterNot(a => a.data == mainOut || a.data == testOut || rewrittenDifferent.exists(_ == a.data))
        (prefix ++ rest :+ Attributed.blank(mainOut))
      }

      if (jacocoPluginEnabled.value) build(Some(jmfRewrite.value))
      else build(None)
    }.value,

    // ---- fork so -javaagent is applied
    Test / fork := true,

    // Attach agent for Test
    Test / forkOptions := {
      val fo0     = (Test / forkOptions).value
      val rootDir = (LocalRootProject / baseDirectory).value
      val baseFO  = fo0.withWorkingDirectory(rootDir)

      val cp     = (Test / dependencyClasspath).value
      val agent  = agentJar(cp)
      val dest   = jacocoExecFile.value.getAbsolutePath
      val inc    = jacocoIncludes.value.mkString(":")
      val exc    = jacocoExcludes.value.mkString(":")
      val append = if (jacocoAppend.value) "true" else "false"

      val agentOpt =
        s"-javaagent:${agent.getAbsolutePath}=destfile=$dest,append=$append,output=file,includes=$inc,excludes=$exc,inclbootstrapclasses=false,jmx=false"

      val log = streams.value.log
      log.info(s"[jacoco] setting fork working dir to: $rootDir")

      if (jacocoPluginEnabled.value) {
        log.info(s"[jacoco] agent jar: ${agent.getName} (enabled)")
        baseFO.withRunJVMOptions(baseFO.runJVMOptions :+ agentOpt)
      } else {
        log.info("[jacoco] disabled (jacocoPluginEnabled=false); NOT adding -javaagent")
        baseFO
      }
    },

    // ---- per-module clean
    jacocoClean := {
      val log    = streams.value.log
      val outDir = target.value / "jacoco"
      IO.delete(outDir)
      IO.createDirectory(outDir)
      IO.delete(jmfOutDir.value)
      log.info(s"[jacoco] cleaned: ${outDir.getAbsolutePath}")
    },

    // ---- per-module report
    jacocoReport := {
      val log        = streams.value.log
      val reportDir  = jacocoReportDir.value
      val execFile   = jacocoExecFile.value
      val name       = jacocoReportName.value

      if (!jacocoPluginEnabled.value) {
        IO.createDirectory(reportDir)
        log.info("[jacoco] disabled (jacocoPluginEnabled=false); report no-op")
        reportDir
      } else if (!execFile.exists) {
        val msg = s"[jacoco] exec file missing, skipping report: ${execFile.getAbsolutePath}"
        if (jacocoFailOnMissingExec.value) sys.error(msg) else log.warn(msg)
        IO.createDirectory(reportDir)
        reportDir
      } else {
        val cp = (Test / dependencyClasspath).value
        val cli = cliJar(cp)

        val classesDir = (Compile / classDirectory).value
        val sourcesDir = (Compile / sourceDirectory).value

        IO.createDirectory(reportDir)

        val args = Seq(
          "java",
          "-jar",
          cli.getAbsolutePath,
          "report",
          execFile.getAbsolutePath,
          "--classfiles",
          classesDir.getAbsolutePath,
          "--sourcefiles",
          sourcesDir.getAbsolutePath,
          "--html",
          reportDir.getAbsolutePath,
          "--name",
          name
        )

        log.info(s"[jacoco] report: ${args.mkString(" ")}")
        val code = scala.sys.process.Process(args, baseDirectory.value).!
        if (code != 0) sys.error(s"[jacoco] report failed ($code)")
        reportDir
      }
    }
  )
}
