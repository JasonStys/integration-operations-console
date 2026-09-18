# Testing strategy

The suite follows a test pyramid: most cases isolate domain algorithms, fewer tests start Spring and
the database, and a small browser-facing layer verifies user workflows. Tests are deterministic and
do not use sleep, real networks, or provider credentials.

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
- Vitest enforces 80% line/function/statement and 75% branch coverage.

## Commands

```bash
cd backend && ./mvnw verify
cd frontend && npm ci && npm run verify
node scripts/validate-repository.mjs
node scripts/generate-code-index.mjs --check
docker compose config --quiet
```

CI repeats the fast H2 build and also runs the backend against a PostgreSQL service. CodeQL scans
Java and JavaScript/TypeScript. Build reports are uploaded on every CI run for inspection.

See [test summary](reports/test-summary.md) for the latest recorded result and
[validation report](reports/validation.md) for non-test gates.
