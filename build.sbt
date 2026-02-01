import xerial.sbt.Sonatype._

// ---- global ---------------------------------------------------------------
ThisBuild / organization   := "io.github.moranaapps"
ThisBuild / scalaVersion   := "2.13.14"             // default
ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.21", "2.13.18")
ThisBuild / version        := "1.1.0"
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
      "org.ow2.asm"            %  "asm"                      % "9.6",
      "org.ow2.asm"            %  "asm-commons"              % "9.6",
      "com.github.scopt"       %% "scopt"                    % "3.7.1",
      "org.scalatest"          %% "scalatest"                % "3.1.4" % Test
    ),

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
    publish / skip := false
  )

// AGGREGATOR (don’t publish)
lazy val root = (project in file("."))
  .aggregate(rewriterCore, sbtPlugin)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )
