import com.github.sbt.jacoco.JacocoKeys.jacoco
import sbt.*
import sbt.Keys.*

import scala.sys.process.*

/** Build helper that wires:
 *  - fetch of jacococli.jar (pinned version)
 *  - run your rewriter
 *  - run jacococli report using classes-filtered
 */
object JacocoFilteredReport extends AutoPlugin {
  // Requires sbt-jacoco to be available (added in project/plugins.sbt)
  override def requires = plugins.JvmPlugin
  override def trigger  = noTrigger

  object autoImport {
    // versions/paths
    val jacocoCliVersion         = settingKey[String]("JaCoCo CLI version (pinned)")
    val jacocoCliJar             = settingKey[File]("Path to tools/jacococli-<ver>.jar")

    // rewriter config
    val coverageRewriteInputDir  = settingKey[File]("Input classes dir to rewrite")
    val coverageRewriteOutputDir = settingKey[File]("Output classes dir (filtered)")
    val coverageRulesFile        = settingKey[File]("Rules file path")

    // tasks
    val fetchJacocoCli           = taskKey[File]("Fetch jacococli.jar into tools/")
    val runCoverageRewriter      = taskKey[Unit]("Run the coverage rewriter")
    val coverageReportFiltered   = taskKey[Unit]("Generate JaCoCo report from classes-filtered")
    val coverageFiltered         = taskKey[Unit]("Run jacoco -> rewrite -> filtered report")
  }
  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    // Pin CLI version & location
    jacocoCliVersion := "0.8.12",
    jacocoCliJar := baseDirectory.value / "tools" / s"jacococli-${jacocoCliVersion.value}.jar",

    // Make jacococli a resolvable dependency (nodeps classifier)
    libraryDependencies +=
      "org.jacoco" % "org.jacoco.cli" % jacocoCliVersion.value classifier "nodeps",

    // Default rewrite paths & rules
    coverageRewriteInputDir  := (Compile / classDirectory).value,
    coverageRewriteOutputDir := target.value / s"scala-${scalaBinaryVersion.value}" / "classes-filtered",
    coverageRulesFile        := baseDirectory.value / "rules" / "coverage-rules.txt",

    // Copy resolved jacococli to tools/ (no curl needed)
    fetchJacocoCli := {
      val out    = jacocoCliJar.value
      val report = update.value              // evaluate once, outside if
      val log    = streams.value.log         // evaluate once, outside if

      IO.createDirectory(out.getParentFile)

      if (!out.exists()) {
        val jarOpt = report.matching(
          moduleFilter(organization = "org.jacoco", name = "org.jacoco.cli") &&
            artifactFilter(`type` = "jar", classifier = "nodeps")
        ).headOption

        jarOpt match {
          case Some(src) =>
            IO.copyFile(src, out)
            log.info(s"Copied JaCoCo CLI to $out")
          case None =>
            sys.error("Could not resolve org.jacoco:org.jacoco.cli:jar:nodeps; check libraryDependencies.")
        }
      } else {
        log.info(s"JaCoCo CLI already present: $out")
      }

      out
    },

    // HOW to call your rewriter: adjust main class/module if needed
    runCoverageRewriter := Def.taskDyn {
      val in  = coverageRewriteInputDir.value.getAbsolutePath
      val out = coverageRewriteOutputDir.value.getAbsolutePath
      val rul = coverageRulesFile.value.getAbsolutePath
      IO.createDirectory(coverageRewriteOutputDir.value)

      // If the rewriter is in this same build as a module called "rewriterCore"
      // and the main class is io.moranaapps.jacocomethodfilter.CoverageRewriter:
      Def.sequential(
        Def.task {
          streams.value.log.info(s"Rewriting classes: in=$in out=$out rules=$rul")
        },
        // Change `Compile / runMain` scoping to the appropriate project if needed
        (Compile / runMain).toTask(
          s" io.moranaapps.jacocomethodfilter.CoverageRewriter --in $in --out $out --rules $rul"
        )
      )
    }.value,

    // Re-report with jacococli using filtered classfiles
    coverageReportFiltered := {
      val log   = streams.value.log
      val jar   = fetchJacocoCli.value // ensures jar is present
      val exec  = (target.value / s"scala-${scalaBinaryVersion.value}" / "jacoco.exec").getAbsolutePath
      val cls   = coverageRewriteOutputDir.value.getAbsolutePath
      val html  = (target.value / s"scala-${scalaBinaryVersion.value}" / "jacoco-report-filtered" / "html").getAbsolutePath
      val xml   = (target.value / s"scala-${scalaBinaryVersion.value}" / "jacoco-report-filtered" / "jacoco.xml").getAbsolutePath
      IO.createDirectory(file(html))

      val srcs = Seq(
        (Compile / scalaSource).value,
        (Compile / javaSource).value
      ).filter(_.exists).flatMap(p => Seq("--sourcefiles", p.getAbsolutePath))

      val cmd = Seq("java", "-jar", jar.getAbsolutePath, "report", exec,
        "--classfiles", cls, "--html", html, "--xml", xml) ++ srcs

      log.info(s"[jacococli] ${cmd.mkString(" ")}")
      val code = cmd.!
      if (code != 0) sys.error(s"jacococli report failed (exit $code)")
      else log.info(s"Filtered report → $html")
    },

    // High-level task: run jacoco (tests) → rewrite → filtered report
    coverageFiltered := Def.sequential(
      Def.task { jacoco.value },
      runCoverageRewriter,
      coverageReportFiltered
    ).value
  )
}
