package morana.coverage

import sbt._

/** Public keys exported by the sbt plugin. */
object Keys {
  object autoImport {
    val jacocoPluginEnabled = settingKey[Boolean]("Marker for JaCoCo plugin participation")
    val jacocoClean         = taskKey[Unit]("Clean JaCoCo outputs")
    val jacocoReport        = taskKey[File]("Generate per-module JaCoCo report")

    val jacocoVersion    = settingKey[String]("JaCoCo version")
    val jacocoExecFile   = settingKey[File]("Per-module JaCoCo .exec file (Test)")
    val jacocoItExecFile = settingKey[File]("Per-module JaCoCo .exec file (IntegrationTest)")
    val jacocoReportDir  = settingKey[File]("Per-module report directory")
    val jacocoIncludes   = settingKey[Seq[String]]("Include patterns (JaCoCo syntax)")
    val jacocoExcludes   = settingKey[Seq[String]]("Exclude patterns (JaCoCo syntax)")
    val jacocoAppend     = settingKey[Boolean]("Append to existing .exec instead of overwrite (default: false)")
    val jacocoFailOnMissingExec = settingKey[Boolean](
      "Fail jacocoReport if .exec is missing (default: false – warn & skip)"
    )

    val jacocoReportName = settingKey[String]("Title used for JaCoCo HTML report")

    // Root-only helpers (NO MERGE): just run per-module tasks across aggregated projects
    val jacocoCleanAll  = taskKey[Unit]("Run jacocoClean in all aggregated modules (no merge)")
    val jacocoReportAll = taskKey[Unit]("Run jacocoReport in all aggregated modules (no merge)")

    val jacocoSetUserDirToBuildRoot = settingKey[Boolean](
      "Mimic non-forked runs by setting -Duser.dir to the build root for forked tests"
    )

    // ---- JMF integration
    val jmfCoreVersion     = settingKey[String]("JMF core library version")
    val Jmf                = config("jmf").extend(Compile).hide
    val jmfRewrite         = taskKey[File]("Rewrite compiled classes using JMF tool; returns output dir")
    val jmfOutDir          = settingKey[File]("JMF output base dir")
    val jmfRulesFile       = settingKey[File]("JMF rules file")
    val jmfCliMain         = settingKey[String]("Main class of the JMF CLI")
    val jmfDryRun          = settingKey[Boolean]("Dry-run rewriter")
    val jmfEnabled         = settingKey[Boolean]("Enable JMF rewriting")
    val jmfPrepareForTests = taskKey[Unit]("Run JMF rewrite when enabled")
    val jmfInitRules       = taskKey[File]("Create default jmf-rules.txt if it does not exist")
    val jmfInitRulesForce  = settingKey[Boolean]("Force overwrite existing jmf-rules.txt (default: false)")
    val jmfRulesTemplate   = settingKey[String]("Template type: 'scala' (default) or 'scala-java'")
    val jmfVerify          = taskKey[Unit]("On-demand scan: show which methods would be excluded from coverage by current rules")
  }
}
