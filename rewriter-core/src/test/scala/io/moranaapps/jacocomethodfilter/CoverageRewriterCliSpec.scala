package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path, Paths}

class CoverageRewriterCliSpec extends AnyFunSuite {

  private def newTempDir(prefix: String): Path = {
    val p = Files.createTempDirectory(prefix)
    p.toFile.deleteOnExit()
    p
  }

  private def newTempFile(prefix: String, suffix: String): Path = {
    val p = Files.createTempFile(prefix, suffix)
    p.toFile.deleteOnExit()
    p
  }

  test("parse should succeed with valid minimal args (global-rules)") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt")
    )
    assert(result.isDefined)
    assert(result.get.in == inDir)
    assert(result.get.out.contains(outDir))
    assert(result.get.globalRules.contains("rules.txt"))
    assert(result.get.localRules.isEmpty)
  }

  test("parse should succeed with valid minimal args (local-rules)") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val rulesFile = newTempFile("jmf-rules-", ".txt")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--local-rules", rulesFile.toString)
    )
    assert(result.isDefined)
    assert(result.get.in == inDir)
    assert(result.get.out.contains(outDir))
    assert(result.get.localRules.contains(rulesFile))
    assert(result.get.globalRules.isEmpty)
  }

  test("parse should succeed with both global and local rules") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val localRules = newTempFile("jmf-local-rules-", ".txt")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "global.txt", "--local-rules", localRules.toString)
    )
    assert(result.isDefined)
    assert(result.get.globalRules.contains("global.txt"))
    assert(result.get.localRules.contains(localRules))
  }

  test("parse should fail when --out is missing in non-verify mode") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt")
    )
    assert(result.isEmpty)
  }

  test("parse should succeed in verify mode without --out") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.verify)
    assert(result.get.out.isEmpty)
  }

  test("parse should fail when both global-rules and local-rules are missing") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString)
    )
    assert(result.isEmpty)
  }

  test("parse should handle dry-run flag correctly") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt", "--dry-run")
    )
    assert(result.isDefined)
    assert(result.get.dryRun)
  }

  test("parse should handle verify flag correctly") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.verify)
    assert(!result.get.dryRun)
  }

  test("parse should handle verify-suggest-includes flag correctly") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify", "--verify-suggest-includes")
    )
    assert(result.isDefined)
    assert(result.get.verify)
    assert(result.get.verifySuggestIncludes)
  }

  test("parse should handle combination of dry-run and verify") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt", "--dry-run", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.dryRun)
    assert(result.get.verify)
  }

  test("parse should fail when --in does not exist") {
    val baseDir = newTempDir("jmf-base-")
    val missingInDir = baseDir.resolve("does-not-exist")
    val rulesFile = newTempFile("jmf-rules-", ".txt")
    val result = CoverageRewriterCli.parse(
      Array("--in", missingInDir.toString, "--local-rules", rulesFile.toString, "--verify")
    )
    assert(result.isEmpty)
  }

  test("parse should fail when --in is not a directory") {
    val inFile = newTempFile("jmf-in-file-", ".bin")
    val rulesFile = newTempFile("jmf-rules-", ".txt")
    val result = CoverageRewriterCli.parse(
      Array("--in", inFile.toString, "--local-rules", rulesFile.toString, "--verify")
    )
    assert(result.isEmpty)
  }

  test("parse should fail when --in is missing") {
    val result = CoverageRewriterCli.parse(
      Array("--out", "/path/to/out", "--global-rules", "rules.txt")
    )
    assert(result.isEmpty)
  }

  test("parse should fail when --verify-suggest-includes is used without --verify") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt", "--verify-suggest-includes")
    )
    assert(result.isEmpty)
  }

  test("parse should succeed in verify mode with --out provided") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.verify)
    assert(result.get.out.contains(outDir))
  }

  test("parse should succeed with all valid rewrite flags") {
    val inDir = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val localRules = newTempFile("jmf-rules-", ".txt")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "global.txt", "--local-rules", localRules.toString, "--dry-run")
    )
    assert(result.isDefined)
    assert(result.get.dryRun)
    assert(result.get.globalRules.contains("global.txt"))
    assert(result.get.localRules.contains(localRules))
    assert(!result.get.verify)
  }

  test("parse should handle empty args array") {
    val result = CoverageRewriterCli.parse(Array.empty)
    assert(result.isEmpty)
  }
}
