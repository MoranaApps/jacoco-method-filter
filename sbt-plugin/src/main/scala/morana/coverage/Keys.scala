package morana.coverage

import sbt._
import Keys._

object Keys {
  object autoImport {
    // Rewrite task + knobs
    val coverageRewrite            = taskKey[File]("Rewrite classes by annotating matched methods (outputs filtered dir).")
    val coverageRewriteInputDir    = settingKey[File]("Input classes dir to rewrite.")
    val coverageRewriteOutputDir   = settingKey[File]("Output classes dir (filtered).")
    val coverageRewriteRules       = settingKey[File]("Rules file for method filtering.")
    val coverageRewriteDryRun      = settingKey[Boolean]("Dry-run rewrite (no files written).")
    val coverageRewriteMainClass   = settingKey[String]("Tool main class (fully qualified).")

    // Report task + knobs
    val coverageReportFiltered     = taskKey[File]("Run JaCoCo CLI report against filtered classes.")
    val jacocoExec                 = settingKey[File]("Path to jacoco.exec (or .ec).")
    val jacocoCliJar               = settingKey[File]("Path to jacococli.jar.")
    val sourceRoots                = settingKey[Seq[File]]("Source roots for report mapping.")
    val htmlOut                    = settingKey[File]("HTML report output dir.")
    val xmlOut                     = settingKey[File]("XML report output file.")

    // Convenience alias
    val coverageFiltered           = inputKey[Unit]("Alias: test; coverageRewrite; coverageReportFiltered")
  }
}
