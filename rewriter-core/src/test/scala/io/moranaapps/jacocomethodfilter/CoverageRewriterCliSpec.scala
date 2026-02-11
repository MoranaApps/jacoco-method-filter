package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Paths

class CoverageRewriterCliSpec extends AnyFunSuite {

  test("parse should succeed with valid minimal args (global-rules)") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--out", "/path/to/out", "--global-rules", "rules.txt")
    )
    assert(result.isDefined)
    assert(result.get.in == Paths.get("/path/to/classes"))
    assert(result.get.out.contains(Paths.get("/path/to/out")))
    assert(result.get.globalRules.contains("rules.txt"))
    assert(result.get.localRules.isEmpty)
  }

  test("parse should succeed with valid minimal args (local-rules)") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--out", "/path/to/out", "--local-rules", "rules.txt")
    )
    assert(result.isDefined)
    assert(result.get.in == Paths.get("/path/to/classes"))
    assert(result.get.out.contains(Paths.get("/path/to/out")))
    assert(result.get.localRules.contains(Paths.get("rules.txt")))
    assert(result.get.globalRules.isEmpty)
  }

  test("parse should succeed with both global and local rules") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--out", "/path/to/out", "--global-rules", "global.txt", "--local-rules", "local.txt")
    )
    assert(result.isDefined)
    assert(result.get.globalRules.contains("global.txt"))
    assert(result.get.localRules.contains(Paths.get("local.txt")))
  }

  test("parse should fail when --out is missing in non-verify mode") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--global-rules", "rules.txt")
    )
    assert(result.isEmpty)
  }

  test("parse should succeed in verify mode without --out") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.verify)
    assert(result.get.out.isEmpty)
  }

  test("parse should fail when both global-rules and local-rules are missing") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--out", "/path/to/out")
    )
    assert(result.isEmpty)
  }

  test("parse should handle dry-run flag correctly") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--out", "/path/to/out", "--global-rules", "rules.txt", "--dry-run")
    )
    assert(result.isDefined)
    assert(result.get.dryRun)
  }

  test("parse should handle verify flag correctly") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.verify)
    assert(!result.get.dryRun)
  }

  test("parse should handle verify-suggest-includes flag correctly") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--global-rules", "rules.txt", "--verify", "--verify-suggest-includes")
    )
    assert(result.isDefined)
    assert(result.get.verify)
    assert(result.get.verifySuggestIncludes)
  }

  test("parse should handle combination of dry-run and verify") {
    val result = CoverageRewriterCli.parse(
      Array("--in", "/path/to/classes", "--out", "/path/to/out", "--global-rules", "rules.txt", "--dry-run", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.dryRun)
    assert(result.get.verify)
  }

  test("parse should fail when --in is missing") {
    val result = CoverageRewriterCli.parse(
      Array("--out", "/path/to/out", "--global-rules", "rules.txt")
    )
    assert(result.isEmpty)
  }

  test("parse should handle empty args array") {
    val result = CoverageRewriterCli.parse(Array.empty)
    assert(result.isEmpty)
  }
}
