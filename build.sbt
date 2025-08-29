import scala.collection.immutable.Seq

ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "io.github.moranaapps"
ThisBuild / publishMavenStyle := true
ThisBuild / version      := "0.1.4"

// sbt plugin must use the modern style
import xerial.sbt.Sonatype.*
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / publishTo              := sonatypePublishToBundle.value
ThisBuild / sbtPluginPublishLegacyMavenStyle := false
ThisBuild / sonatypeBundleDirectory := target.value / "sonatype-staging"

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

// --- aggregator only
lazy val root = (project in file("."))
  .aggregate(rewriterCore)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )
