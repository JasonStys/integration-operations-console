# Integration Operations Console

[![CI](https://github.com/JasonStys/integration-operations-console/actions/workflows/ci.yml/badge.svg)](https://github.com/JasonStys/integration-operations-console/actions/workflows/ci.yml)
[![CodeQL](https://github.com/JasonStys/integration-operations-console/actions/workflows/codeql.yml/badge.svg)](https://github.com/JasonStys/integration-operations-console/actions/workflows/codeql.yml)

A full-stack reliability laboratory for operating deterministic third-party data integrations. It
models the awkward parts of production integration work—cursor pagination, rate limits, transient
and permanent failures, idempotency, retry delay, dead-letter recovery, audit evidence, and
operational metrics—without contacting a real provider or requiring credentials.

The project demonstrates Java 21, Spring Boot, TypeScript, React, SQL, Bash, Docker, API-first
development, automated testing, and secure continuous integration in one deliberately compact
repository.

Version 1.1.0 also provides a self-contained Windows application image: open its EXE to
start the bundled Java service, native status/quit window and real browser interface.
Its OS-local H2 workspace retains accounts, jobs, retry state and audit history between
launches. No Java installation, IDE, Node, Python or Docker is required by the app user.
See the [native application guide](docs/native-application.md) and
[observed native validation](docs/reports/native-application-2026-11-10.md).
This is an unsigned portable app-image; a published installer is not claimed.

## What it demonstrates

- Two fictional, deterministic connector adapters with cursor pagination and normalized records.
  Discovery choices retain ascending connector-ID order across JVMs and bean discovery order.
- Durable accounts, jobs, idempotency keys, retry state, and append-only audit events.
- Capped exponential backoff with deterministic jitter, rate-limit handling, dead-lettering,
  replay, cancellation, and correlation IDs.
- Keyset pagination and supporting SQL indexes rather than offset scans.
- Search the complete job database by account and state, including older jobs outside the ten-row
  activity feed. Query-bound cursors prevent accidental reuse with different filters.
- Capture current job state and a bounded audit window together, browse older events, and download
  the exact displayed review. Retry availability is explicit; historical events never masquerade
  as a reconstruction of historical state.
- Link several simulated accounts per connector and distinguish a retry of the same sync request
  from a new request using visible idempotency keys.
- A responsive, keyboard-usable operations console backed by OpenAPI-generated TypeScript types.
- Health probes and Prometheus metrics for operational diagnosis.
- Enforced Java and browser coverage, linting, formatting, schema migration, build, and repository
  documentation checks.

## Quick start

For a prepared Windows app-image, retain the complete extracted directory and open
`IntegrationOperationsConsole.exe`. The small native window opens the console and
provides a safe quit action; the browser includes guided first-workflow help.
Normal workspace data lives outside the app directory and Git. The app is a private
local lab with synthetic roles/providers, not a public multiuser service.

For the existing container topology, use Docker Desktop with Compose:

```bash
docker compose up --build
```

Open <http://localhost:8081>. The stack contains the React web application, Spring Boot API, and
PostgreSQL. Every connector and failure is simulated locally.

To run the scripted workflow after the stack is healthy:

```bash
bash scripts/demo.sh
```

Stop containers with `docker compose down`. Add `--volumes` only when you intentionally want to
delete the local demonstration database.

## Local development

Requirements are Java 21 and Node.js 24. Maven itself does not need to be installed because the
repository includes a Maven 3.9.16 wrapper.

```bash
cd backend
./mvnw spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

The API uses an in-memory H2 database in PostgreSQL compatibility mode unless `IOC_DATABASE_*`
variables are set. The browser dev server is <http://localhost:5173>; the API is
<http://localhost:8080>.

Run the complete quality gate on a Unix-like shell:

```bash
bash scripts/verify.sh
```

On Windows, run `backend\mvnw.cmd verify`, then `npm ci` and `npm run verify` in `frontend`, and
finally the two Node scripts documented in [Testing](docs/testing.md).

## Architecture at a glance

```text
React console ──HTTP/OpenAPI──> Spring REST adapter ──> application service
                                                        │        │
                                           fake connectors      JDBC port
                                                                 │
                                               H2 (tests/dev) or PostgreSQL
```

The backend is a modular monolith: HTTP, orchestration, connector, domain, and persistence concerns
have explicit boundaries while one deployable keeps the demonstration easy to run. Details and
trade-offs are in [Architecture](docs/architecture.md) and
[ADR 0001](docs/adr/0001-modular-monolith.md).

## Major feature guide

| Feature              | Behavior                                                                                   | Primary implementation                             |
| -------------------- | ------------------------------------------------------------------------------------------ | -------------------------------------------------- |
| Connector simulation | Stable pages plus planned rate-limit, transient, and schema failures                       | `connector/`                                       |
| Job lifecycle        | Validated queued → running → retry/success/dead-letter/cancel transitions                  | `domain/SyncJob.java`                              |
| Delivery safety      | Unique idempotency key and account identity constraints                                    | SQL migration and JDBC store                       |
| Retry control        | Capped exponential delay with repeatable per-job jitter                                    | `service/RetryPolicy.java`                         |
| Operations           | Run-next/run-one, cancel, replay, history, status counts                                   | service and REST controller                        |
| Observability        | Correlation response header, audit events, health probes, Prometheus counters              | web filter, store, Actuator                        |
| Typed UI             | OpenAPI-generated DTO types and a fetch adapter                                            | `docs/openapi.yaml`, `frontend/src/api/`           |
| Complete job review  | Account/state filters, bounded history pages, captured state/delay and exact JSON download | `frontend/src/JobReview.tsx`, `store/`, `service/` |
| Verification         | Unit, integration, API, component, accessibility, characterization, style, and build gates | test trees and workflows                           |

## API summary

Read endpoints expose connectors, accounts, jobs, job history, and dashboard counts. Mutation
endpoints link an account, submit a job, run work, cancel it, or replay a dead-lettered job.
State-changing demonstration requests require `X-Demo-Role: operator`; job submission also requires
an `Idempotency-Key`. This header check illustrates an authorization boundary but is not real
authentication. See [API guide](docs/api.md) and the complete
[OpenAPI contract](docs/openapi.yaml).

For a realistic operator or viewer walkthrough, including request-key reuse and delayed work,
see [Job review](docs/job-review.md). These are local simulated integrations: the demo role header
is not an account system and should never be used to protect real customer information.

## Repository guide

| File or directory                                                                                                            | Responsibility                                                                               |
| ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `README.md`, `LICENSE`, `CHANGELOG.md`                                                                                       | Entry point, MIT terms, and release history                                                  |
| `CONTRIBUTING.md`, `SECURITY.md`                                                                                             | Contribution quality bar and private vulnerability reporting                                 |
| `.editorconfig`, `.gitignore`                                                                                                | Cross-editor formatting and generated/local exclusions                                       |
| `compose.yaml`                                                                                                               | Local web/API/PostgreSQL topology with health-based startup                                  |
| `.github/workflows/ci.yml`                                                                                                   | Java, web, PostgreSQL, repository, and artifact checks                                       |
| `.github/workflows/codeql.yml`                                                                                               | Java and JavaScript/TypeScript static security analysis                                      |
| `.github/dependabot.yml`                                                                                                     | Weekly Maven, npm, Docker, and workflow dependency updates                                   |
| `backend/pom.xml`, `checkstyle.xml`                                                                                          | Dependencies, Java toolchain, coverage and style gates                                       |
| `backend/mvnw*`, `.mvn/wrapper/*`                                                                                            | Pinned cross-platform Maven bootstrap                                                        |
| `backend/Dockerfile`, `.dockerignore`                                                                                        | Verified multi-stage non-root API image                                                      |
| `backend/src/main/resources/application.yml`                                                                                 | Safe H2 defaults and environment-controlled production settings                              |
| `backend/src/main/resources/db/migration/V1__operations_schema.sql`                                                          | Tables, constraints, and scheduler/listing indexes                                           |
| `backend/src/main/resources/db/migration/V2__filtered_job_indexes.sql`                                                       | Non-destructive indexes for filtered job lists and bounded audit windows                     |
| `backend/.../OperationsConsoleApplication.java`                                                                              | Spring entry point and UTC clock dependency                                                  |
| `backend/.../domain/*.java`                                                                                                  | Immutable account, job, status, failure-plan, and audit models                               |
| `backend/.../connector/*.java`                                                                                               | Fake-provider contract, pages, records, registry, failures, and token bucket                 |
| `backend/.../store/*.java`                                                                                                   | Persistence port, cursor codec, and JDBC implementation                                      |
| `backend/.../service/*.java`                                                                                                 | Retry math and transactional lifecycle orchestration                                         |
| `backend/.../web/*.java`                                                                                                     | REST DTOs/controller, validation errors, CORS, role boundary, and correlation IDs            |
| `backend/src/test/.../SyncJobTest.java`                                                                                      | Domain transition and invariant tests                                                        |
| `backend/src/test/.../TokenBucketTest.java`                                                                                  | Rate-limiter refill and rejection tests                                                      |
| `backend/src/test/.../RetryPolicyTest.java`                                                                                  | Backoff cap and deterministic-jitter properties                                              |
| `backend/src/test/.../OperationsServiceIntegrationTest.java`                                                                 | Migration, persistence, idempotency, pagination, processing, and audit tests                 |
| `backend/src/test/.../OperationsControllerIntegrationTest.java`                                                              | HTTP contract, authorization, and validation tests                                           |
| `backend/src/test/.../OperationsReviewIntegrationTest.java`, `store/PageCursorTest.java`                                     | Complete filtered paging, bounded history, exact request-key binding, legacy/invalid cursors |
| `backend/src/test/.../PerformanceCharacterizationTest.java`                                                                  | Large-sample algorithm timing guardrail                                                      |
| `frontend/package*.json`                                                                                                     | Exact web dependency graph and repeatable commands                                           |
| `frontend/tsconfig*.json`, `vite.config.ts`, `vitest.config.ts`                                                              | Strict TypeScript, production bundling, and coverage configuration                           |
| `frontend/eslint.config.mjs`, `.prettier*`                                                                                   | Static analysis and deterministic formatting policy                                          |
| `frontend/index.html`, `src/main.tsx`, `src/styles.css`                                                                      | Accessible document shell, React mount, and responsive visual system                         |
| `frontend/src/App.tsx`, `components/StatusBadge.tsx`                                                                         | Operator workflow, status metrics, actions, and semantic state labels                        |
| `frontend/src/JobReview.tsx`, `JobReview.test.tsx`                                                                           | Filtered read-only review, cancellation of stale UI results, downloads and regressions       |
| `frontend/src/api/client.ts`, `schema.d.ts`                                                                                  | Typed HTTP boundary and generated OpenAPI declarations                                       |
| `frontend/src/*.test.tsx`, `src/api/*.test.ts`, `src/test/setup.ts`                                                          | Component, accessibility, interaction, and HTTP adapter tests                                |
| `frontend/Dockerfile`, `.dockerignore`, `nginx.conf`                                                                         | Verified static image, security headers, SPA routing, and same-origin proxy                  |
| `frontend/playwright.config.ts`, `tests/browser-server.mjs`, `tests/check-browser-fixture.mjs`, `tests/browser/*.browser.ts` | Owned real-Java browser fixtures, isolation guards and desktop/mobile acceptance             |
| `scripts/verify.sh`, `demo.sh`                                                                                               | Full quality gate and runnable end-to-end API demonstration                                  |
| `scripts/validate-repository.mjs`, `generate-code-index.mjs`                                                                 | Documentation/header invariants and exact source line index                                  |
| `docs/`                                                                                                                      | Design, API, data, complexity, testing, security, operations, reports, and code index        |

The expanded documentation map is in [Repository map](docs/repository-map.md). Exact locations of
functions, classes, and important variables are generated in [Code index](docs/code-index.md).

## Evidence and limitations

The committed [test summary](docs/reports/test-summary.md) and
[validation report](docs/reports/validation.md) state exactly what was run and distinguish local
results from CI. The project intentionally avoids real OAuth, secrets, external APIs, distributed
worker claiming, and production identity management; see [Limitations](docs/limitations.md).
The [latest maintenance audit](docs/reports/maintenance-audit-2026-09-18.md) records the current
hosted runs and dependency compatibility decisions.

The [practical workflow report](docs/reports/practical-workflows-2026-11-09.md) records this
upgrade's acceptance evidence and remaining deployment boundaries.

Created by [Jason Stys](https://github.com/JasonStys); contact through the profile links or this
repository's issue tracker for non-sensitive questions.

## License

MIT. See [LICENSE](LICENSE).

## Documentation editions

Start with the [organized handbook](docs/handbook.md) for current usage, design, security and test guidance. Reviewed downloadable editions: [project-guide.docx](docs/reports/project-guide.docx).

[Publication identities and source scope](docs/reports/publications.json) · [Documentation validation record](docs/reports/documentation-refresh-2026-11-09.md). Historical results retain their own tested revision and date.
