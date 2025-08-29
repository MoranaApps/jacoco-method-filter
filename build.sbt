import xerial.sbt.Sonatype._

ThisBuild / organization := "io.github.moranaapps"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / version      := "0.1.4"

ThisBuild / publishMavenStyle := true
ThisBuild / versionScheme     := Some("early-semver")

// Central Portal bundle + host
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
ThisBuild / publishTo              := sonatypePublishToBundle.value

// Central dislikes legacy checksums; keep only .asc signatures
ThisBuild / publish / checksums := Nil

// Optional: make sure docs/sources are published
ThisBuild / Compile / packageDoc / publishArtifact := true
ThisBuild / Compile / packageSrc / publishArtifact := true

// ---- MODULES ----

// core library (this is the only thing we publish now)
lazy val rewriterCore = (project in file("rewriter-core"))
  .settings(
    name := "jacoco-method-filter-core",
    publish / skip := false,
    libraryDependencies ++= Seq(
      "org.ow2.asm" % "asm"         % "9.6",
      "org.ow2.asm" % "asm-commons" % "9.6",
      "com.github.scopt" %% "scopt" % "4.1.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    Compile / mainClass := Some("io.moranaapps.jacocomethodfilter.CoverageRewriter"),
    // quiet scaladoc link warnings
    Compile / doc / scalacOptions ++= Seq("-no-link-warnings")
  )

// aggregator (don’t publish)
lazy val root = (project in file("."))
  .aggregate(rewriterCore)
  .settings(
    name := "jacoco-method-filter",
    publish / skip := true
  )
