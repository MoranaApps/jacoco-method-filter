import xerial.sbt.Sonatype._
import sbtassembly.AssemblyPlugin.autoImport._

// ---- global ---------------------------------------------------------------
ThisBuild / organization   := "io.github.moranaapps"
ThisBuild / scalaVersion   := "2.12.21"             // default
ThisBuild / crossScalaVersions := Seq("2.12.21")
ThisBuild / version        := "2.0.0"
ThisBuild / versionScheme  := Some("early-semver")

// Central (bundle flow)
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / publishTo              := sonatypePublishToBundle.value

// Project metadata
ThisBuild / homepage := Some(url("https://github.com/MoranaApps/jacoco-method-filter"))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scmInfo  := Some(
  ScmInfo(
    url("https://github.com/MoranaApps/jacoco-method-filter"),
    "scm:git:git@github.com:MoranaApps/jacoco-method-filter.git"
  )
)
ThisBuild / developers := List(
  Developer("moranaapps", "MoranaApps", "miroslavpojer@seznam.cz", url("https://github.com/MoranaApps"))
)

// ---- modules --------------------------------------------------------------

// CORE LIB (publish this)
lazy val rewriterCore = (project in file("rewriter-core"))
  .settings(
    name := "jacoco-method-filter-core",
    publish / skip := false,

    // CLI entrypoint you already have
    Compile / mainClass := Some("io.moranaapps.jacocomethodfilter.CoverageRewriter"),

    libraryDependencies ++= Seq(
      "org.ow2.asm"            %  "asm"                      % "9.7.1",
      "org.ow2.asm"            %  "asm-commons"              % "9.7.1",
      "com.github.scopt"       %% "scopt"                    % "3.7.1",
      "org.scalatest"          %% "scalatest"                % "3.1.4" % Test
    ),

    // ---- Fat JAR: bundle all runtime deps so consumers have zero transitive dependencies ----
    // Replace the default packageBin JAR with the assembly fat JAR
    Compile / packageBin := assembly.value,

    assembly / assemblyJarName := s"${name.value}_${scalaBinaryVersion.value}-${version.value}.jar",

    // Exclude scala-library and scopt from the fat JAR to avoid version conflicts
    assembly / assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp.filter { f =>
        val name = f.data.getName
        name.startsWith("scala-library") || name.startsWith("scala-reflect") || name.startsWith("scopt_")
      }
    },

    // Shade/relocate dependencies to avoid classpath conflicts (e.g. ASM version clash with JaCoCo)
    assembly / assemblyShadeRules := Seq(
      ShadeRule.rename("org.objectweb.asm.**" -> "jmf.shaded.asm.@1").inAll
    ),

    // Merge strategy for duplicates
    assembly / assemblyMergeStrategy := {
      val log = sLog.value
      val warnFirst: String => sbtassembly.MergeStrategy = (path: String) =>
        sbtassembly.CustomMergeStrategy("warn-first", 1) {
          (deps: Vector[sbtassembly.Assembly.Dependency]) =>
            if (deps.size > 1) {
              log.warn(s"assembly merge conflict for '$path' (${deps.size} entries); keeping the first one")
            }
            sbtassembly.MergeStrategy.first(deps)
        }

      {
        case PathList("META-INF", xs @ _*) => sbtassembly.MergeStrategy.discard
        case "module-info.class"           => sbtassembly.MergeStrategy.discard
        case x                             => warnFirst(x)
      }
    },

    // Strip ASM dependencies from the published POM (they're shaded/bundled)
    // but keep scala-library and scopt as dependencies
    pomPostProcess := { (node: scala.xml.Node) =>
      import scala.xml._
      import scala.xml.transform._
      val transformed = new RuleTransformer(new RewriteRule {
        override def transform(n: Node): Seq[Node] = n match {
          case e: Elem if e.label == "dependencies" =>
            // Keep scala-library and scopt, remove ASM
            val filtered = e.child.filter { dep =>
              val artifactId = (dep \ "artifactId").text
              artifactId.startsWith("scala-library") || artifactId.startsWith("scopt_")
            }
            if (filtered.isEmpty) NodeSeq.Empty
            else Elem(e.prefix, e.label, e.attributes, e.scope, e.minimizeEmpty, filtered: _*)
          case other => other
        }
      }).transform(node).head

      // Validate: the published POM must have exactly scala-library and scopt
      val pomDeps = (transformed \\ "dependency").map { dep =>
        (dep \ "groupId").text + ":" + (dep \ "artifactId").text
      }
      val expectedPrefixes = Set("org.scala-lang:scala-library", "com.github.scopt:scopt_")
      val missing = expectedPrefixes.filterNot(prefix => pomDeps.exists(_.startsWith(prefix)))
      if (missing.nonEmpty) {
        sys.error(
          s"pomPostProcess validation failed: expected dependencies matching $expectedPrefixes " +
            s"but these are missing: $missing. Actual POM deps: $pomDeps"
        )
      }
      val unexpected = pomDeps.filterNot(d => expectedPrefixes.exists(d.startsWith))
      if (unexpected.nonEmpty) {
        sys.error(
          s"pomPostProcess validation failed: unexpected dependencies in published POM: $unexpected. " +
            s"Only $expectedPrefixes should remain after filtering."
        )
      }

      transformed
    },

    Compile / doc / scalacOptions ++= Seq("-no-link-warnings")
  )

// SBT PLUGIN (publish this)
lazy val sbtPlugin = (project in file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "jacoco-method-filter-sbt",
    // sbt plugins are built with Scala 2.12 for sbt 1.x
    scalaVersion := "2.12.21",
    crossScalaVersions := Seq("2.12.21"),
    // Prevent publishing the legacy (non-suffixed) Maven artifacts like
    // `jacoco-method-filter-sbt-<ver>.jar` which Sonatype Central cannot associate
    // with the sbt-plugin coordinates `jacoco-method-filter-sbt_2.12_1.0`.
    sbtPluginPublishLegacyMavenStyle := false,
    publish / skip := false,
    
    // Copy template from repo root to resources during build
    Compile / resourceGenerators += Def.task {
      val templateSource = (LocalRootProject / baseDirectory).value / "jmf-rules.template.txt"
      val templateTarget = (Compile / resourceManaged).value / "jmf-rules.template.txt"
      IO.copyFile(templateSource, templateTarget)
      Seq(templateTarget)
    }.taskValue
  )

// AGGREGATOR (don’t publish)
lazy val root = (project in file("."))
  .aggregate(rewriterCore, sbtPlugin)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )
