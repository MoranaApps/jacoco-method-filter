ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "io.github.moranaapps"
ThisBuild / publishMavenStyle := true
ThisBuild / version      := "0.1.1"

ThisBuild / sbtPluginPublishLegacyMavenStyle := false

// --- core tool (2.13)
lazy val rewriterCore = (project in file("rewriter-core"))
  .settings(
    name := "jacoco-method-filter-core",
    libraryDependencies ++= Seq(
      "org.ow2.asm" % "asm"          % "9.6",
      "org.ow2.asm" % "asm-commons"  % "9.6",
      "com.github.scopt" %% "scopt"  % "4.1.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    Compile / mainClass := Some("io.moranaapps.jacocomethodfilter.CoverageRewriter")
  )

// --- sbt plugin (2.12 / sbt 1.x)
lazy val sbtPlugin = (project in file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "jacoco-method-filter-sbt",
    organization := "io.github.moranaapps",
    // plugin must be on 2.12 for sbt 1.x
    scalaVersion := "2.12.19",
    // 👇 this forces modern Maven file names (no _2.12_1.0 suffix in the file name)
    publishMavenStyle := true,
    sbtPluginPublishLegacyMavenStyle := false,
    moduleName := name.value,
    libraryDependencies += "org.jacoco" % "org.jacoco.cli" % "0.8.12" classifier "nodeps"
  )

// --- aggregator only
lazy val root = (project in file("."))
  .aggregate(rewriterCore, sbtPlugin)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )

// CPP target config
ThisBuild / publishTo := {
  val snapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at snapshots) else localStaging.value
}

// Required metadata
ThisBuild / homepage := Some(url("https://github.com/MoranaApps/jacoco-method-filter"))
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/MoranaApps/jacoco-method-filter"),
    "scm:git:git@github.com:MoranaApps/jacoco-method-filter.git"
  )
)
ThisBuild / developers := List(
  Developer("moranaapps", "MoranaApps", "dev@moranaapps.org", url("https://github.com/MoranaApps"))
)
ThisBuild / Compile / doc / scalacOptions ++= Seq("-no-link-warnings")
