package io.moranaapps.jacocomethodfilter

import scopt.OptionParser

import java.nio.file.{Files, Paths}

/** CLI argument parser for CoverageRewriter. */
private[jacocomethodfilter] object CoverageRewriterCli {

  /** Parses command-line arguments into a validated CliConfig.
    *
    * @param args Command-line arguments.
    * @return Some(config) if parsing succeeds, None if parsing fails or --help is used.
    */
  def parse(args: Array[String]): Option[CliConfig] =
    parser.parse(args, CliConfig())

  private lazy val parser: OptionParser[CliConfig] =
    new OptionParser[CliConfig]("jacoco-method-filter") {
      opt[String]("in")
        .required()
        .action((v, c) => c.copy(in = Paths.get(v)))
        .text("Input classes directory")

      opt[String]("out")
        .optional()
        .action((v, c) => c.copy(out = Some(Paths.get(v))))
        .text("Output classes directory (required unless --verify is used)")

      opt[String]("global-rules")
        .optional()
        .action((v, c) => c.copy(globalRules = Some(v)))
        .text("Global rules file path or URL")

      opt[String]("local-rules")
        .optional()
        .action((v, c) => c.copy(localRules = Some(Paths.get(v))))
        .text("Local rules file path")

      opt[Unit]("dry-run")
        .action((_, c) => c.copy(dryRun = true))
        .text("Only print matches; do not modify classes")

      opt[Unit]("verify")
        .action((_, c) => c.copy(verify = true))
        .text("Read-only scan: list all methods that would be excluded by rules")

      opt[Unit]("error-on-unmatched")
        .action((_, c) => c.copy(errorOnUnmatched = true))
        .text("Exit non-zero if any rules matched zero methods (requires --verify)")

      opt[Unit]("strict")
        .action((_, c) => c.copy(strict = true))
        .text("Exit non-zero if any rules have no id: label (unlabelled-rule enforcement)")

      opt[String]("report-file")
        .optional()
        .action((v, c) => c.copy(reportFile = Some(Paths.get(v))))
        .text("Write filtered-methods report to this file (works with --verify, --dry-run, and rewrite)")

      opt[String]("report-format")
        .optional()
        .action((v, c) => c.copy(reportFormat = v.toLowerCase))
        .validate(v =>
          if (Set("txt", "json", "csv").contains(v.toLowerCase)) success
          else failure("--report-format must be one of: txt, json, csv")
        )
        .text("Report format: txt (default), json, or csv")

      checkConfig { cfg =>
        if (!Files.isDirectory(cfg.in)) {
          failure("--in must exist and be a directory")
        } else if (!cfg.verify && cfg.out.isEmpty) {
          failure("--out is required when not in verify mode")
        } else if (cfg.globalRules.isEmpty && cfg.localRules.isEmpty) {
          failure("At least one of --global-rules or --local-rules must be specified")
        } else if (cfg.reportFile.exists(Files.isDirectory(_))) {
          failure("--report-file must be a file path, not an existing directory")
        } else if (cfg.reportFile.isEmpty && cfg.reportFormat != "txt") {
          failure("--report-format requires --report-file to be set")
        } else if (cfg.errorOnUnmatched && !cfg.verify) {
          failure("--error-on-unmatched requires --verify")
        } else {
          success
        }
      }
    }
}
