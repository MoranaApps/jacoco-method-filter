# Copilot Review Rules

Purpose
- Define consistent review behavior and response formatting for Copilot code reviews
- Keep responses concise; do not generate long audit reports unless requested

Mode: Default review

- Scope
  - Single PR, normal risk
- Priorities (in order)
  - correctness → security → tests → maintainability → style
- Checks
  - Correctness
    - Highlight logic bugs, missing edge cases, regressions, and contract changes
  - Security and data handling
    - Flag unsafe input handling, secrets exposure, auth/authz issues, and insecure defaults
  - Tests
    - Check that tests exist for changed logic and cover success + failure paths
  - Maintainability
    - Point out unnecessary complexity, duplication, and unclear naming/structure
  - Style
    - Note style issues only when they reduce readability or break repo conventions
- Response format
  - Use short bullet points
  - Reference files + line ranges where possible
  - Group comments by severity: Blocker (must fix), Important (should fix), Nit (optional)
  - Provide actionable suggestions (what to change), not rewrites
  - Do NOT rewrite the whole PR or produce long reports

Mode: Double-check review

- Scope
  - Higher-risk PRs (security, infra, money flows, wide refactors, data migrations)
- Additional focus
  - Confirm previous review comments were correctly addressed (if applicable)
  - Re-check high-risk areas: filesystem writes (bytecode rewrite), rule parsing edge cases, backward compatibility
  - Look for hidden side effects: rollout/upgrade path, failure modes, idempotency
  - Validate safe defaults: least privilege, secure logging, safe error messages
- Response format
  - Only add comments where risk/impact is non-trivial
  - Avoid repeating minor style notes already covered by default review
  - Call out "risk acceptance" explicitly if something is left as-is

Commenting rules (all modes)

- Always include:
  - What is the issue (1 line)
  - Why it matters (impact/risk)
  - How to fix (minimal actionable suggestion)
- Prefer linking to existing patterns in the repo over introducing new ones
- If you cannot be certain (missing context), ask a targeted question instead of assuming

Non-goals

- Do not request refactors unrelated to the PR's intent
- Do not bikeshed formatting if tools (formatter/linter) handle it
- Do not propose architectural rewrites unless explicitly requested

PR Body Management

- Treat PR description as a changelog; append new changes at the end
- Must not rewrite/replace entire PR body; always append updates
- Structure:
  - Keep original description at top
  - Add new sections/updates chronologically below
  - Use headings like `## Update [YYYY-MM-DD]` referencing commit hashes
- Purpose: preserve review history and decision trail

Repo additions

- Domain-specific high-risk areas:
  - Filesystem writes (bytecode rewriting)
  - Rule parsing/matching semantics
  - Backward compatibility of CLI flags/exit codes
- Contract-sensitive outputs:
  - rules file syntax and matching semantics
  - CLI flags and exit codes
  - sbt plugin keys and integration behavior
- Required tests: ScalaTest in `rewriter-core/src/test/scala`
- Repo-specific style: prefer explicit Scala; avoid clever tricks
