import com.github.sbt.jacoco.JacocoPlugin.autoImport.{ jacocoSkip, jacocoDataFile }
import sbt.*
import sbt.Keys.*

import scala.sys.process.*

object JacocoFilteredSettings extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = com.github.sbt.jacoco.JacocoPlugin
  import com.github.sbt.jacoco.JacocoPlugin.autoImport.*

  // hidden tool config so these deps don't leak into your app
  val Jmf = config("jmf").hide

  object autoImport {
    val jmfRulesFile        = settingKey[File]("Rules file (globs) for method filtering")
    val jmfOutDir           = settingKey[File]("Output dir for filtered artifacts")
    val jmfCliMain          = settingKey[String]("Main class of the rewriter CLI")
    val jacocoExecFile      = taskKey[File]("Locate jacoco.exec produced by sbt-jacoco")
    val jmfRewrite          = taskKey[File]("Rewrite compiled classes to classes-filtered using rules")
    val jacocoFiltered      = taskKey[(File, File)]("Render filtered HTML+XML from jacoco.exec + classes-filtered")
    val jmfDryRun           = settingKey[Boolean]("If true, log matches but don't modify bytecode")
    val jmfScalaVersion     = settingKey[String]("Scala version required by the rewriter core (2.13.x)")
    val jmfCoreVersion  = settingKey[String]("Version of jacoco-method-filter-core to use")
    val jmfScalaBinary  = settingKey[String]("Scala binary version used to pick the core artifact")
    val jmfSourceDirNames   = settingKey[Seq[String]]("Names under src/main to include as source roots for JaCoCo (manual list).")
    val jmfExtraSourceDirs  = settingKey[Seq[File]]("Extra source directories (absolute or relative to module) to include.")
    val jmfBaseTestFullClasspath = taskKey[Classpath]("Snapshot of Test/fullClasspath before JMF overrides.")
    val jmfMaybeExecFile   = taskKey[Option[File]]("Optional path to Test jacoco.exec (if present).")
  }
  import autoImport.*

  override def projectSettings: Seq[Setting[_]] =
    // 1) single settings first
    Seq(
      ivyConfigurations += Jmf
    ) ++
    // 2) splice in all default settings for the custom config (so managedClasspath exists)
    inConfig(Jmf)(Defaults.configSettings) ++
    // 3) the rest
    Seq(
      // pick the right suffix per subproject
      jmfScalaBinary := scalaBinaryVersion.value,
      jmfCoreVersion := "0.1.7",

      // tool deps live ONLY on Jmf
      libraryDependencies ++= Seq(
        // core rewriter (published for 2.12 and 2.13 now)
        "io.github.moranaapps" % s"jacoco-method-filter-core_${jmfScalaBinary.value}" % jmfCoreVersion.value % Jmf.name,
        // jacoco cli fat jar (for the report)
        "org.jacoco"           % "org.jacoco.cli"    % "0.8.12"              % Jmf.name classifier "nodeps",
        // NEW: jacoco agent jar we’ll use with -javaagent
        "org.jacoco"           % "org.jacoco.agent"  % "0.8.12"              % Jmf.name classifier "runtime"
      ),

      // defaults (override per module if needed)
      jmfRulesFile      := (ThisBuild / baseDirectory).value / "jacoco-method-filter-rules.txt",
      jmfOutDir         := target.value / "jacoco-filtered",
      jmfCliMain        := "io.moranaapps.jacocomethodfilter.CoverageRewriter",
      jmfDryRun         := false,

      jmfSourceDirNames  := Seq("scala", "java", "kotlin"),
      jmfExtraSourceDirs := Seq(baseDirectory.value / "src" / "generated" / "scala"),

      jmfMaybeExecFile := {
        val log      = streams.value.log
        val expected = (Test / jacocoDataFile).value // e.g. target/scala-2.12/jacoco/data/jacoco.exec
        log.info(s"[jmf] expected exec path: ${expected.getAbsolutePath}")
        if (expected.exists) Some(expected)
        else {
          val found = (target.value ** GlobFilter("jacoco.exec")).get.filter(_.isFile)
            .sortBy(_.lastModified()).lastOption
          found.foreach(f => log.warn(s"[jmf] expected path missing; using newest under target: ${f.getAbsolutePath}"))
          found
        }
      },

      jacocoExecFile := {
        val log      = streams.value.log
        val expected = (Test / jacocoDataFile).value
        log.info(s"[jmf] expecting fresh exec at: ${expected.getAbsolutePath}")
        if (expected.exists) expected
        else {
          val found = (target.value ** GlobFilter("jacoco.exec")).get.filter(_.isFile)
            .sortBy(_.lastModified()).lastOption
          found.getOrElse {
            sys.error(
              s"[jmf] jacoco.exec not found. Expected at: ${expected.getAbsolutePath} " +
                s"or anywhere under: ${target.value.getAbsolutePath}. " +
                s"Did `Test / jacoco` run in this subproject?"
            )
          }
        }
      },

      jmfBaseTestFullClasspath := (Test / fullClasspath).value,

      // rewrite step
      jmfRewrite := {
        val _ = (Compile / compile).value  // force classes to exist
        val classesIn = (Compile / classDirectory).value
        if (!classesIn.exists())
          sys.error(s"[jmf] compiled classes not found: ${classesIn.getAbsolutePath}")
        val hasClasses = (classesIn ** sbt.GlobFilter("*.class")).get.nonEmpty
        if (!hasClasses)
          sys.error(s"[jmf] no .class files under ${classesIn.getAbsolutePath}. Nothing to rewrite.")

        val log       = streams.value.log
        val outDir    = jmfOutDir.value / "classes-filtered"
        IO.delete(outDir); IO.createDirectory(outDir)

        val toolJars = (Jmf / update).value
          .matching(artifactFilter(`type` = "jar"))
          .distinct
        // debug
        log.info("[jmf] tool CP:\n" + toolJars.map(f => s"  - ${f.getAbsolutePath}").mkString("\n"))

        val cpStr = toolJars.mkString(java.io.File.pathSeparator)
        val args = Seq(
          "java", "-cp", cpStr, jmfCliMain.value,
          "--in", classesIn.getAbsolutePath,
          "--out", outDir.getAbsolutePath,
          "--rules", jmfRulesFile.value.getAbsolutePath,
        ) ++ (if (jmfDryRun.value) Seq("--dry-run") else Seq())

        log.info(s"[jmf] rewrite: ${args.mkString(" ")}")
        val code = Process(args, baseDirectory.value).!
        if (code != 0) sys.error(s"[jmf] rewriter failed ($code)")
        outDir
      },

      // report step
      jacocoFiltered := {
        // STEP 1: capture logger & expected exec path up front
        val log         = streams.value.log

        val jacocoWorkDir = (Test / crossTarget).value / "jacoco"
        val instrDir      = jacocoWorkDir / "instrumented-classes"
        if (instrDir.exists) {
          log.info(s"[jmf] removing stale offline dir: ${instrDir.getAbsolutePath}")
          IO.delete(instrDir)
        }

        // choose the standard sbt-jacoco path so tooling stays happy:
        val expectedExec = (Test / jacocoDataFile).value       // e.g. target/scala-2.12/jacoco/data/jacoco.exec
        IO.createDirectory(expectedExec.getParentFile)         // agent won't create parent dirs

        // STEP 2: ensure filtered classes exist (rewriter runs after compile)
        val classesDir  = jmfRewrite.value
        val filteredCount = (classesDir ** GlobFilter("*.class")).get.size
        log.info(s"[jmf] filtered classes count: $filteredCount in ${classesDir.getAbsolutePath}")
        require(filteredCount > 0, s"[jmf] classes-filtered is empty — rewrite didn’t produce any .class files")

        val compiledDir = (Compile / classDirectory).value
        val backupDir   = jmfOutDir.value / "backup-original-classes"

        // STEP 3: delete stale exec (don’t rely on task ordering/races)

        // small helper for dir copies
        def copyDir(from: File, to: File): Unit = {
          IO.createDirectory(to)
          IO.copyDirectory(from, to)
        }

        // STEP 4: swap compiled -> filtered ON DISK (only for this run)
        log.info(s"[jmf] backing up compiled classes from: ${compiledDir.getAbsolutePath}")
        IO.delete(backupDir)
        if (compiledDir.exists()) { IO.createDirectory(backupDir); IO.copyDirectory(compiledDir, backupDir) }
        else IO.createDirectory(compiledDir)

        // copy only .class files from filtered → compiled, overwriting originals
        val filteredClasses = (classesDir ** GlobFilter("*.class")).pair(Path.relativeTo(classesDir)).toMap
        filteredClasses.foreach { case (src, rel) =>
          val dst = compiledDir / rel
          IO.createDirectory(dst.getParentFile)
          IO.copyFile(src, dst, preserveLastModified = true)
        }
        log.info(s"[jmf] overlaid ${filteredClasses.size} class files into ${compiledDir.getAbsolutePath}")

        try {
          // STEP 5: run tests via sbt-jacoco (writes exec for the *filtered* bytes)
          // pick one sample and compare hashes in filtered vs compiled
          def md5(f: File): String = {
            val md = java.security.MessageDigest.getInstance("MD5")
            md.digest(sbt.io.IO.readBytes(f)).map("%02x".format(_)).mkString
          }

          val allFiltered = (classesDir ** GlobFilter("*.class")).get
          val sampleOpt   = allFiltered.headOption
          sampleOpt.foreach { src =>
            val rel = IO.relativize(classesDir, src).get
            val dst = compiledDir / rel
            val dstMd5 = if (dst.exists) md5(dst) else "MISSING"
            log.info(s"[jmf] SAMPLE rel=$rel filteredMD5=${md5(src)} compiledMD5=$dstMd5")
          }

          val compiledCount = (compiledDir ** GlobFilter("*.class")).get.size
          log.info(s"[jmf] compiledDir now points at filtered bytes: ${compiledDir.getAbsolutePath} (count=$compiledCount)")
          val compiledSample = (compiledDir ** GlobFilter("*.class")).get.take(5).map(_.getName).mkString(", ")
          log.info(s"[jmf] sample classes in compiledDir: $compiledSample")

          // --- run tests once with JaCoCo agent (ONLINE instrumentation) ---
          // --- run tests once with JaCoCo agent (ONLINE instrumentation) ---
          IO.createDirectory(expectedExec.getParentFile)
          // don't delete expectedExec here; it can be created late on JVM exit

          // resolve agent JAR
          val updateRpt = (Jmf / update).value
          val agentJar  = updateRpt.allFiles.find(_.getName.startsWith("org.jacoco.agent-"))
            .getOrElse(sys.error("[jmf] jacoco agent jar not resolved (org.jacoco.agent:runtime)"))

          // build agent option to WRITE TO expectedExec
          val agentOpt = s"-javaagent:${agentJar.getAbsolutePath}=destfile=${expectedExec.getAbsolutePath},append=false,output=file"

          val st0       = state.value
          val extracted = Project.extract(st0)
          val pr        = thisProjectRef.value            // <-- current subproject ref
          val moduleBase  = (ThisProject / baseDirectory).value
          val forkOpts0   = (Test / forkOptions).value
          val javaOpts0   = (Test / javaOptions).value

          // scope temp settings to THIS subproject only
          val tempSettings: Seq[Setting[_]] = Seq(
            // turn OFF sbt-jacoco’s offline path everywhere for this run
            Global / jacocoSkip := true,
            // (belt & suspenders) also off on this module
            pr / jacocoSkip     := true,

            // ensure tests fork and run from the module root (fixes file: api/src/test/resources/... lookups)
            pr / Test / fork        := true,
            pr / Test / forkOptions := forkOpts0.withWorkingDirectory(Some(moduleBase)),

            // inject agent; also strip any pre-existing -javaagent to avoid duplicates
            pr / Test / javaOptions := javaOpts0.filterNot(_.startsWith("-javaagent:")) :+ agentOpt
          )

          log.info(s"[jmf] running tests with JaCoCo agent: ${agentJar.getName}")
          log.info(s"[jmf] using agentOpt: " + agentOpt)

          // append settings and explicitly select the project before running
          val st1: State = extracted.appendWithSession(tempSettings.toList, st0)
          val stSel      = Command.process(s"project ${pr.project}", st1)

          // (important) EVALUATE javaOptions to prove the agent flag is present
          val (st2, effOpts) = Project.extract(stSel).runTask(pr / Test / javaOptions, stSel)
          effOpts.foreach(o => log.info("[jmf] Test/javaOptions: " + o))

          // run the tests for THIS subproject
          val (st3, _) = Project.extract(st2).runTask(pr / Test / test, st2)

          // wait for expectedExec (agent writes on JVM exit)
          def waitForExec(f: File, timeoutMs: Int = 120000, pollMs: Int = 200): File = {
            val deadline = System.nanoTime() + timeoutMs.toLong * 1000000L
            var last = -1L; var stable = 0
            while (System.nanoTime() < deadline) {
              if (f.exists()) {
                val len = f.length()
                if (len > 0 && len == last) { stable += 1; if (stable >= 2) return f }
                else { stable = 0; last = len }
              }
              Thread.sleep(pollMs.toLong)
            }
            sys.error(s"[jmf] jacoco.exec not present/stable after ${timeoutMs}ms at ${f.getAbsolutePath}")
          }
          val execFile = waitForExec(expectedExec)
          // --- end agent block ---

        } finally {
          // STEP 6: restore original classes no matter what
          log.info(s"[jmf] restoring original classes to: ${compiledDir.getAbsolutePath}")
          if (backupDir.exists()) {
            log.info(s"[jmf] restoring original classes to: ${compiledDir.getAbsolutePath}")
            IO.copyDirectory(backupDir, compiledDir, preserveLastModified = true)
            IO.delete(backupDir)
          }
        }

        // STEP 7: wait until exec exists AND is stable (avoids FS race) - REMOVED - renumber later

        // STEP 8: choose source roots from your manual lists
        def hasAnySources(d: File): Boolean =
          d.exists && (
            (d ** GlobFilter("*.scala")).get.nonEmpty ||
              (d ** GlobFilter("*.java")).get.nonEmpty  ||
              (d ** GlobFilter("*.kt")).get.nonEmpty
            )

        val srcMain       = baseDirectory.value / "src" / "main"
        val wanted        = jmfSourceDirNames.value.map(srcMain / _)
        val extra         = jmfExtraSourceDirs.value
        val candidateDirs = (wanted ++ extra).distinct
        val filteredSrcs  = candidateDirs.filter(hasAnySources)
        log.info("[jmf] source roots:\n" + filteredSrcs.map(f => s"  - ${f.getPath}").mkString("\n"))

        // STEP 9: render via JaCoCo CLI
        val htmlOut  = jmfOutDir.value / "html"
        val xmlOut   = jmfOutDir.value / "jacoco.filtered.xml"
        IO.delete(htmlOut); IO.createDirectory(htmlOut)

        val cliJar = (Jmf / update).value
          .matching(moduleFilter("org.jacoco","org.jacoco.cli") && artifactFilter(`type`="jar", classifier="nodeps"))
          .headOption.getOrElse(sys.error("[jmf] org.jacoco.cli (nodeps) not resolved"))

        val cmd = Seq(
          "java", "-jar", cliJar.getAbsolutePath,
          "report", expectedExec.getAbsolutePath,
          "--classfiles", classesDir.getAbsolutePath
        ) ++ filteredSrcs.flatMap(d => Seq("--sourcefiles", d.getAbsolutePath)) ++ Seq(
          "--html", htmlOut.getAbsolutePath,
          "--xml",  xmlOut.getAbsolutePath
        )

        log.info(s"[jmf] jacococli: ${cmd.mkString(" ")}")
        val code = scala.sys.process.Process(cmd, baseDirectory.value).!
        if (code != 0) sys.error(s"[jmf] jacococli report failed ($code)")
        log.info(s"[jmf] HTML: ${(htmlOut / "index.html").getAbsolutePath}")
        log.info(s"[jmf] XML : ${xmlOut.getAbsolutePath}")
        (htmlOut, xmlOut)
      }
    )
}
