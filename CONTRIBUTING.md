# Contributing

Keep changes narrow, self-documenting, and backed by a failing test or a clear validation case.
Public methods and non-obvious algorithms need concise rationale; comments should explain decisions,
not restate syntax. Update the OpenAPI contract before changing shared request or response shapes.

Before proposing a change:

1. Run `node scripts/generate-code-index.mjs` after source edits.
2. Run `bash scripts/verify.sh` from the repository root.
3. If SQL changes, add a new forward-only Flyway migration—never edit an applied migration.
4. Update the relevant document and `CHANGELOG.md`.
5. Do not add credentials, personal data, live provider endpoints, or copied proprietary material.

Commit messages should state the outcome in the imperative mood. Pull requests should explain the
problem, design choice, validation performed, and any operational or compatibility risk.
