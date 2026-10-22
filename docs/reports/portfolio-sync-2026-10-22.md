<!-- File: docs/reports/portfolio-sync-2026-10-22.md; Purpose: reviewed integration and validation trail by Jason Stys. -->

# Portfolio synchronization — 2026-10-22

## Follow-up dependency review

Dependabot created additional proposals after the first integration merged.
This separately validated follow-up retains their commit ancestry and reconciles
the complete dependency union rather than restoring an older package manifest.
Vitest and its coverage provider remain matched; current runtime and security
fixes are preserved. All native checks must pass on the new exact source head
before a normal merge. The earlier review below remains historical context.

By Jason Stys. Source baseline: `5301b733f399df060438a56f8b148a2f48c7f0c6`.

Reviewed original proposals: [#28](https://github.com/JasonStys/integration-operations-console/pull/28), [#29](https://github.com/JasonStys/integration-operations-console/pull/29), [#30](https://github.com/JasonStys/integration-operations-console/pull/30), [#31](https://github.com/JasonStys/integration-operations-console/pull/31), [#32](https://github.com/JasonStys/integration-operations-console/pull/32), [#33](https://github.com/JasonStys/integration-operations-console/pull/33), [#34](https://github.com/JasonStys/integration-operations-console/pull/34), [#35](https://github.com/JasonStys/integration-operations-console/pull/35), [#36](https://github.com/JasonStys/integration-operations-console/pull/36), [#37](https://github.com/JasonStys/integration-operations-console/pull/37). Their commit ancestry is retained in a combined integration candidate; a normal merge is permitted only after that exact candidate passes applicable native workflows. No branch protection, test threshold, security scan or failing step is bypassed. Historical references and intentionally separate experiments are preserved.

Dependency manifests are reconciled as the union of proposed dependency changes, not wholesale replacement with an older branch. Overlapping locks are mechanically resolved with the registry tools. Python package constraints and the actual installed CI/runtime locks are kept consistent; Vitest/coverage providers and CodeQL steps are coordinated. Runtime/data directories are not included in Git.

Validation is commit-specific: use this repository's [Actions](https://github.com/JasonStys/integration-operations-console/actions) and integration PR checks. This record describes the reviewed work, not a claim that a future revision has already passed. Default-branch checks are verified again after merge. Private full audit and Actions backups stay on the owner's PC, outside public source.

The React-only proposal is paired with the matching React DOM patch version. CodeQL init/autobuild/analyze are advanced together; Dependabot groups them for future updates.
