# Copilot Review Rules

Default review

- Scope: Single PR, normal risk.
- Priorities (in order): correctness → security → tests → maintainability → style.
- Checks:
  - Highlight logic bugs, missing edge cases, and regressions.
  - Flag security or data‑handling issues.
  - Check that tests exist and cover the changed logic.
  - Point out large complexity / duplication hotspots.
  - For this repo: treat rules parsing/matching, CLI flags/exit codes, and sbt plugin behavior as compatibility surfaces.
- Response format:
  - Use short bullet points.
  - Reference files + line ranges where possible.
  - Do NOT rewrite the whole PR or produce long reports.

Double-check review

- Scope: Higher‑risk PRs (security, infra, money flows, wide refactors).
- Additional focus:
  - Re‑validate that previous review comments were correctly addressed.
  - Re‑check high‑risk areas: filesystem writes (bytecode rewrite), rule parsing edge cases, and backward compatibility.
  - Look for hidden side effects and backward‑compatibility issues.
- Response format:
  - Only add comments where risk/impact is non‑trivial.
  - Avoid repeating minor style notes already covered by default review.

Shared

PR Body Management

- PR body format: Write PR description as a changelog, appending new changes to the end.
- Prohibited: Rewriting or replacing the entire PR body. Always append updates.
- Structure:
  - Keep original description at top
  - Add new sections/updates chronologically below
  - Use clear headings like "## Update [date/commit]" or "## Changes Added"
  - Each update should reference the commit hash that made the changes
- Purpose: Maintain full history of PR evolution for reviewers and future reference.
