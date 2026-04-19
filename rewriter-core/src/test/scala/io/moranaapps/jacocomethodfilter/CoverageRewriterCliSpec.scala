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

  test("parse should accept --report-file in verify mode") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-file", "/tmp/report.txt")
    )
    assert(result.isDefined)
    assert(result.get.reportFile.contains(Paths.get("/tmp/report.txt")))
  }

  test("parse should accept --report-format json") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-file", "/tmp/report.json", "--report-format", "json")
    )
    assert(result.isDefined)
    assert(result.get.reportFormat == "json")
  }

  test("parse should accept --report-format JSON (case-insensitive)") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-file", "/tmp/report.json", "--report-format", "JSON")
    )
    assert(result.isDefined)
    assert(result.get.reportFormat == "json", "format must be normalised to lowercase")
  }

  test("parse should accept --report-format csv") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-file", "/tmp/report.csv", "--report-format", "csv")
    )
    assert(result.isDefined)
    assert(result.get.reportFormat == "csv")
  }

  test("parse should accept --report-format CSV (case-insensitive)") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-file", "/tmp/report.csv", "--report-format", "CSV")
    )
    assert(result.isDefined)
    assert(result.get.reportFormat == "csv", "format must be normalised to lowercase")
  }

  test("parse should reject invalid --report-format value") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-file", "/tmp/report.xml", "--report-format", "xml")
    )
    assert(result.isEmpty)
  }

  test("parse should default reportFile to None and reportFormat to txt") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(result.get.reportFile.isEmpty)
    assert(result.get.reportFormat == "txt")
  }

  test("parse should accept --report-file with --dry-run") {
    val inDir  = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt",
        "--dry-run", "--report-file", "/tmp/dry-run-report.json", "--report-format", "json")
    )
    assert(result.isDefined)
    assert(result.get.dryRun)
    assert(result.get.reportFile.contains(Paths.get("/tmp/dry-run-report.json")))
    assert(result.get.reportFormat == "json")
  }

  test("parse should reject --report-file pointing to an existing directory") {
    val inDir = newTempDir("jmf-in-")
    val dirAsReport = newTempDir("jmf-report-dir-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-file", dirAsReport.toString)
    )
    assert(result.isEmpty, "--report-file pointing to a directory must be rejected")
  }

  test("parse should reject --report-format without --report-file") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-format", "json")
    )
    assert(result.isEmpty, "--report-format without --report-file must be rejected")
  }

  test("parse should accept --report-format txt without --report-file (default no-op)") {
    // txt is the default; specifying it explicitly without a file path is redundant but not an error
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-format", "txt")
    )
    assert(result.isDefined, "--report-format txt without --report-file is allowed (no-op)")
  }

  test("parse should accept --report-format TXT (uppercase) without --report-file (default no-op)") {
    // uppercase TXT normalises to txt before checkConfig; must be treated same as lowercase txt
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--report-format", "TXT")
    )
    assert(result.isDefined, "--report-format TXT without --report-file is allowed (normalised to txt)")
    assert(result.get.reportFormat == "txt", "TXT must be normalised to lowercase txt")
  }

  // --- --error-on-unmatched ---

  test("parse should accept --error-on-unmatched with --verify") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify", "--error-on-unmatched")
    )
    assert(result.isDefined)
    assert(result.get.errorOnUnmatched)
    assert(result.get.verify)
  }

  test("parse should reject --error-on-unmatched without --verify") {
    val inDir  = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt", "--error-on-unmatched")
    )
    assert(result.isEmpty, "--error-on-unmatched without --verify must be rejected")
  }

  test("parse should default errorOnUnmatched to false") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(!result.get.errorOnUnmatched)
  }

  test("parse should accept --error-on-unmatched combined with --report-file") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--error-on-unmatched", "--report-file", "/tmp/report.json", "--report-format", "json")
    )
    assert(result.isDefined)
    assert(result.get.errorOnUnmatched)
    assert(result.get.reportFormat == "json")
  }

  // --- --strict ---

  test("parse should accept --strict flag") {
    val inDir  = newTempDir("jmf-in-")
    val outDir = newTempDir("jmf-out-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--out", outDir.toString, "--global-rules", "rules.txt", "--strict")
    )
    assert(result.isDefined)
    assert(result.get.strict)
  }

  test("parse should default strict to false") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify")
    )
    assert(result.isDefined)
    assert(!result.get.strict)
  }

  test("parse should accept --strict with --verify") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify", "--strict")
    )
    assert(result.isDefined)
    assert(result.get.strict)
    assert(result.get.verify)
  }

  test("parse should accept --strict combined with --error-on-unmatched") {
    val inDir = newTempDir("jmf-in-")
    val result = CoverageRewriterCli.parse(
      Array("--in", inDir.toString, "--global-rules", "rules.txt", "--verify",
        "--strict", "--error-on-unmatched")
    )
    assert(result.isDefined)
    assert(result.get.strict)
    assert(result.get.errorOnUnmatched)
  }
}
