package morana.coverage

// 1) Exclude the *name* Keys from the wildcard import (kills the shadowing)
import sbt.{Keys => _, _}

// 2) Bring the *members* of sbt.Keys into scope explicitly (fullClasspath, runner, streams, target, etc.)
import sbt.Keys._

// 3) Bring your plugin keys into scope (coverageRewrite, jacocoExec, etc.)
import morana.coverage.Keys.autoImport._

import scala.sys.process._

object JacocoFilterPlugin extends AutoPlugin {
  override def trigger  = noTrigger
  override def requires = plugins.JvmPlugin

  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    // Sensible defaults (all overrideable)
    coverageRewriteInputDir  := (Compile / classDirectory).value,
    coverageRewriteOutputDir := target.value / s"scala-${scalaBinaryVersion.value}" / "classes-filtered",
    coverageRewriteRules     := baseDirectory.value / "rules" / "coverage-rules.txt",
    coverageRewriteDryRun    := false,
    coverageRewriteMainClass := "io.moranaapps.jacocomethodfilter.CoverageRewriter",

    jacocoExec   := target.value / "jacoco.exec",                         // sbt-jacoco default
    jacocoCliJar := baseDirectory.value / "tools" / "jacococli.jar",
    sourceRoots  := Seq((Compile / scalaSource).value, (Compile / javaSource).value).filter(_.exists),
    htmlOut      := target.value / "jacoco-html",
    xmlOut       := target.value / "jacoco.xml",

    // Make resolved JaCoCo CLI accessible as a standalone jar in tools/
    Compile / resourceGenerators += Def.task {
      val log   = streams.value.log
      val jarOut= jacocoCliJar.value
      IO.createDirectory(jarOut.getParentFile)

      // Find the resolved nodeps jar from update report
      val report = update.value
      val jarOpt = report.matching(
        moduleFilter(organization = "org.jacoco", name = "org.jacoco.cli") &&
          artifactFilter(`type` = "jar", classifier = "nodeps")
      ).headOption

      jarOpt.foreach { src =>
        if (!jarOut.exists) {
          IO.copyFile(src, jarOut)
          log.info(s"[jacoco] Copied JaCoCo CLI to $jarOut")
        }
      }
      Seq(jarOut)
    }.taskValue,

    // === coverageRewrite: run your main on the USING project's classpath ===
    coverageRewrite := {
      val log       = streams.value.log
      val inDir     = coverageRewriteInputDir.value
      val outDir    = coverageRewriteOutputDir.value
      val rulesFile = coverageRewriteRules.value
      val dryRun    = coverageRewriteDryRun.value
      val mainCls   = coverageRewriteMainClass.value

      require(!mainCls.trim.isEmpty, "coverageRewriteMainClass must be set.")
      if (!inDir.exists) sys.error(s"[coverageRewrite] Input classes not found: $inDir")
      IO.createDirectory(outDir)

      val args = Seq("--in", inDir.getAbsolutePath, "--out", outDir.getAbsolutePath) ++
        (if (rulesFile.exists) Seq("--rules", rulesFile.getAbsolutePath) else Seq.empty) ++
        (if (dryRun) Seq("--dry-run") else Seq.empty)

      val cp = (Compile / fullClasspath).value.map(_.data)          // using project's CP (includes your core)
      val r  = (Compile / runner).value

      log.info(s"[coverageRewrite] $mainCls ${args.mkString(" ")}")
      r.run(mainCls, cp, args, log).getOrElse(sys.error("[coverageRewrite] Tool failed."))
      outDir
    },

    // === coverageReportFiltered: run JaCoCo CLI ===
    coverageReportFiltered := {
      val log   = streams.value.log
      val cli   = jacocoCliJar.value
      val exec  = jacocoExec.value
      val cls   = coverageRewriteOutputDir.value
      val html  = htmlOut.value
      val xml   = xmlOut.value
      val srcs  = sourceRoots.value

      if (!cli.exists) sys.error(s"[coverageReportFiltered] jacococli.jar not found at: $cli")
      if (!cls.exists) sys.error(s"[coverageReportFiltered] filtered class dir not found: $cls")
      if (!exec.exists) log.warn(s"[coverageReportFiltered] jacoco.exec not found: $exec (report may be empty)")

      IO.createDirectory(html)
      IO.createDirectory(xml.getParentFile)

      val cmd = Seq(
        "java", "-jar", cli.getAbsolutePath, "report", exec.getAbsolutePath,
        "--classfiles", cls.getAbsolutePath,
        "--html", html.getAbsolutePath,
        "--xml",  xml.getAbsolutePath
      ) ++ srcs.flatMap(s => Seq("--sourcefiles", s.getAbsolutePath))

      log.info(s"[jacococli] ${cmd.mkString(" ")}")
      val code = Process(cmd, None).!(log)
      if (code != 0) sys.error(s"[coverageReportFiltered] JaCoCo CLI failed with code $code")
      xml
    },

    // === convenience alias ===
    coverageFiltered := {
      Def.sequential(
        Def.taskDyn((Test / test).map(_ => Def.task(()))),        // run tests (sbt-jacoco writes jacoco.exec)
        Def.taskDyn(coverageRewrite.map(_ => Def.task(()))),      // rewrite to filtered dir
        Def.taskDyn(coverageReportFiltered.map(_ => Def.task(())))// generate report
      ).value
    }
  )
}
