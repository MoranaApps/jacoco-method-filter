import sbt._
import sbt.Keys._
import scala.sys.process._
import sbt.Defaults

object JacocoFilteredSettings extends AutoPlugin {
  override def trigger = allRequirements

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
    val jmfCoreVersion      = settingKey[String]("jacoco-method-filter-core version")
  }
  import autoImport._

  override def projectSettings: Seq[Setting[_]] =
    // 1) single settings first
    Seq(
      ivyConfigurations += Jmf
    ) ++
      // 2) splice in all default settings for the custom config (so managedClasspath exists)
      inConfig(Jmf)(Defaults.configSettings) ++
      // 3) the rest
      Seq(
        jmfScalaVersion := "2.13.14",
        jmfCoreVersion  := "0.1.7",

        // tool deps live ONLY on Jmf
        libraryDependencies ++= Seq(
          "io.github.moranaapps" % "jacoco-method-filter-core_2.12" % jmfCoreVersion.value % Jmf.name,
          "org.scala-lang"       % "scala-library"                  % jmfScalaVersion.value % Jmf.name,
          "org.jacoco"           % "org.jacoco.cli"                 % "0.8.12"              % Jmf.name classifier "nodeps"
        ),

        // defaults (override per module if needed)
        jmfRulesFile      := (ThisBuild / baseDirectory).value / "jacoco-method-filter-rules.txt",
        jmfOutDir         := target.value / "jacoco-filtered",
        jmfCliMain        := "io.moranaapps.jacocomethodfilter.CoverageRewriter",
        jmfDryRun         := false,

        // find jacoco.exec produced by sbt-jacoco
        jacocoExecFile := {
          val cands =
            (target.value ** "scala-*").get.flatMap(p => (p / "jacoco" / "jacoco.exec").get) ++
              (target.value ** "jacoco.exec").get
          cands.headOption.getOrElse(sys.error(s"[jmf] jacoco.exec not found under ${target.value}. Run `sbt jacoco` first."))
        },

        // rewrite step
        jmfRewrite := {
          val log       = streams.value.log
          val classesIn = (Compile / classDirectory).value
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
          val log         = streams.value.log
          val exec        = jacocoExecFile.value
          val classesDir  = jmfRewrite.value
          val srcDirs     = (Compile / unmanagedSourceDirectories).value
          val htmlOut     = jmfOutDir.value / "html"
          val xmlOut      = jmfOutDir.value / "jacoco.filtered.xml"
          IO.delete(htmlOut); IO.createDirectory(htmlOut)

          val cliJar = (Jmf / update).value
            .matching(
              moduleFilter("org.jacoco", "org.jacoco.cli") &&
              artifactFilter(`type` = "jar", classifier = "nodeps")
            )
            .headOption
            .getOrElse(sys.error("[jmf] org.jacoco.cli (nodeps) not resolved"))
          log.info(s"[jmf] jacococli jar: ${cliJar.getName}")

          val cmd = Seq(
            "java", "-jar", cliJar.getAbsolutePath,
            "report", exec.getAbsolutePath,
            "--classfiles", classesDir.getAbsolutePath
          ) ++ srcDirs.flatMap(d => Seq("--sourcefiles", d.getAbsolutePath)) ++ Seq(
            "--html", htmlOut.getAbsolutePath,
            "--xml",  xmlOut.getAbsolutePath
          )

          log.info(s"[jmf] jacococli: ${cmd.mkString(" ")}")
          val code = Process(cmd, baseDirectory.value).!
          if (code != 0) sys.error(s"[jmf] jacococli report failed ($code)")
          log.info(s"[jmf] HTML: ${(htmlOut / "index.html").getAbsolutePath}")
          log.info(s"[jmf] XML : ${xmlOut.getAbsolutePath}")
          (htmlOut, xmlOut)
        }
      )
}
