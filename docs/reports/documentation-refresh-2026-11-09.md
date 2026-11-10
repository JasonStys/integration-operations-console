# Documentation refresh — 2026-11-09

By Jason Stys.

This documentation-only edition describes source `2563b4154d2e7755516d12911d68c4a32d654905`. It does not change application behavior, accounts, private data, installers or published releases.

## Published editions and completed review

| File | Bytes | SHA-256 | Reviewed scope |
| --- | ---: | --- | --- |
| [project-guide.docx](project-guide.docx) | 46229 | `bdfcf6e8253974674753a896b10bb198c0c55c1054de014c95835ee9441fe435` | 6 pages, each viewed individually |

Word review checked plain black titles, readable pagination, whole table rows, source references, practical limits and absence of clipping or overflow. Render receipts were matched to the current DOCX digest before approval.

## Evidence and change boundary

- Functional baseline: `2563b4154d2e7755516d12911d68c4a32d654905`.
- Workflow observation: `2026-11-10T05:25:40.628667+00:00`; exact run URLs and source identities remain in [publications.json](publications.json).
- Local document-package validation verifies exact copied bytes, Office ZIP integrity, approved page/tab identities and internal documentation links. Repository-specific documentation and source-policy commands are run separately and recorded in the integration handoff.
- Source/index metadata is regenerated using the repository’s existing tools. Existing privacy, security, coverage and workflow gates are retained.
- PR and later main workflows must pass independently. This record does not claim future or unrun integration CI success.

## Interpretation

The Word files document the cited source; dated source reports retain their original execution scope. A passing workflow is distinct from completed analysis with zero findings, and neither establishes production suitability. Checksums identify exact bytes, not a signature or publisher attestation.

Use the [handbook](../handbook.md) to choose usage, design, security and test documentation. Keep editable Markdown and reviewed editions together; do not replace historical measurements with undated claims.

## Integration-local checks

Observed at `2026-11-10T08:11:50.240561+00:00` on Windows with this repository’s existing development tools. Python commands use its isolated development environment. The documentation source baseline remains the immutable revision above; these checks validate this documentation integration locally.

| Command | Observed result |
| --- | --- |
| `node scripts/generate-code-index.mjs` | Passed, exit 0. |
| `node scripts/generate-code-index.mjs --check` | Passed, exit 0. |
| `node scripts/validate-repository.mjs` | Passed, exit 0. |
| `npm run verify (frontend)` | Passed, exit 0. |
| `mvn --batch-mode --no-transfer-progress --offline verify (backend; existing isolated Maven cache)` | Passed, exit 0. |

The independent publication audit also matched every copied Office file to its approved SHA-256, checked ZIP package integrity and current page/tab approvals, compared immutable workflow/source metadata, and resolved every local handbook/report link within the repository. Hosted PR/main, browser, platform and container jobs remain separate required checks; they are not inferred from these local results.



The unchanged Java verify lifecycle passed 36 tests with zero failures, errors or skipped tests, the original JaCoCo gate and zero Checkstyle violations. The frontend full verify gate passed all 25 tests at 93.97% line coverage (156/166 lines), lint, strict type generation and production build. Maven used the existing offline cache and disposable H2 state; PostgreSQL and actual browser acceptance remain separate required hosted lanes. Existing H2/Flyway-version and Mockito-agent notices were emitted; no warning was suppressed. This documentation integration changed no backend, frontend, provider credential or user account.
