# Testing strategy

The suite follows a test pyramid: most cases isolate domain algorithms, fewer tests start Spring and
the database, and a small browser-facing layer verifies user workflows. Tests are deterministic and
do not use provider credentials. Actual browser tests use owned loopback services and isolated
synthetic databases; unit tests inject deterministic collaborators.

## Backend

- Domain unit tests cover legal/illegal job transitions and terminal-state protection.
- Token-bucket tests cover capacity, exhaustion, and clock-driven refill.
- Retry-policy tests verify deterministic jitter, monotonic growth, and the cap across many IDs.
- Service integration tests run Flyway against H2 PostgreSQL mode and cover account/job deduplication,
  pagination, successful multipage work, permanent failure, replay, cancellation, and audit order.
- Controller integration tests cover the HTTP contract, role boundary, validation, correlation IDs,
  and problem responses.
- A characterization test executes 250,000 constant-time policy/rate-limit decisions and uses a
  generous guardrail to catch accidental algorithmic regressions without claiming a benchmark.

## Frontend

- HTTP adapter tests verify headers, serialization, successful parsing, and safe error handling.
- React tests use a fake client to verify initial loading, account and job workflows, scheduler
  actions, audit rendering, error status, semantic labels, and accessible names.
- Vitest enforces the unchanged 75% line/function/statement and 65% branch thresholds from its
  configuration. Coverage percentages describe executed tests, not proof of correctness.
- Complete-list and captured-review tests exercise filtering/pagination, empty/error/retry states,
  exact downloads, out-of-order responses, busy/revision invalidation and the real HTTP client.

## Commands

```bash
cd backend && ./mvnw verify
cd frontend && npm ci && npm run verify
node scripts/validate-repository.mjs
node scripts/generate-code-index.mjs --check
docker compose config --quiet
```

Actual browser acceptance additionally requires a Java21 `JAVA_HOME` and browser binaries:

```bash
cd backend && ./mvnw --batch-mode verify && cd ../frontend
npm ci && npm run build
npx playwright install chromium
node tests/check-browser-fixture.mjs
npm run test:browser
```

`IOC_E2E_API_PORT`/`IOC_E2E_UI_PORT` override18961/18962; they must be distinct unused loopback
ports. `IOC_E2E_JAVA` can name an explicit Java21 binary. The fixture owns the packaged API process,
fresh in-memory H2 database and static UI server, refuses occupied/invalid ports before HTTP and
stops only its own children. Browser tests have zero retries and use real routes and mutations.

CI repeats the fast H2 build and also runs the backend against a PostgreSQL service. CodeQL scans
Java and JavaScript/TypeScript. Build reports are uploaded on every CI run for inspection.

See [test summary](reports/test-summary.md) for the latest recorded result and
[validation report](reports/validation.md) for non-test gates.

The [2026-11-09 workflow report](reports/practical-workflows-2026-11-09.md) supersedes those initial
publication counts for this feature. Browser acceptance starts an owned packaged API with a fresh
in-memory database and built static UI; it never reuses a user's existing server or database.
