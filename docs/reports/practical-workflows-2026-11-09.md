# Practical workflow validation — 2026-11-09

Author: Jason Stys. Scope: fictional local integrations, synthetic accounts/jobs; no live providers,
credentials or user databases. Hosted acceptance must pass at the reviewed commit before merge.

## Changes and independent evidence

- Complete account/state-filtered job search with query-bound keyset navigation reaches jobs beyond
  the ten-row activity feed. Bounds, empty queries, changed-filter cursors, legacy cursors and
  unchanged multi-page completeness are exercised through actual JDBC/Flyway/Spring requests.
- Captured current job/clock/audit windows use a read-only repeatable-read transaction. Independent
  history fixtures prove all event IDs appear once across bounded pages, with exact null endings.
  JSON download is exactly the displayed capture; old events are not historical state snapshots.
- Visible provider reference and request key distinguish several accounts, exact submission retry
  and separate work. Reusing a key for another account or sync policy fails without changing state.
- Fixed the real HTTP client's unbound Run-next method call. Mock clients alone had hidden this.
- Fixed Java nanosecond timestamps rounding forward at the SQL microsecond boundary, which could
  reject an immediate run or change a retried response. A fixed .123456789Z clock must persist and
  return .123456Z, deduplicate identically and run both provider pages without a sleep.
- Actual browser tests caught omitted required-null JSON fields and populated mobile grid overflow.
  Page/review DTOs now explicitly serialize null; layout contains wide tables in named keyboard-
  reachable scroll regions. Assertions remain strict, with no blanket overflow hiding.
- Corrected HTML Unicode-v patterns for literal hyphens and UUID-state type inference; invalid
  references/keys are blocked by both browser form constraints and backend validation.
- Compatible scoped Minimatch advisory fixes preserve the full dependency audit with zero findings.

## Completed local gates

| Gate | Recorded result |
|---|---|
| Java21 / Maven3.9.16 `verify` |31tests passed;0failed/errored/skipped; original JaCoCo gate and0Checkstyle violations |
| Real migrations | Flyway V1+additive V2 passed against fresh H2 PostgreSQL-compatible schema |
| React/HTTP unit suite |25passed;93.97%lines,90.95%statements,88.37%functions,92.24%branches |
| New read-only review |100%lines/functions;97.64%branches, including stale/out-of-order/client/revision/busy invalidation |
| Actual compiled UI + packaged Java API |14desktop/mobile Chromium journeys passed; no API mocks |
| Browser oracles |21-job paging;36-event25+11windows; exact JSON download without requery/mutation; five accepted Atlas records; viewer403 and unchanged state/history |
| Browser safety/usability |Invalid HTML inputs, inert hostile display name, empty/terminal/delayed/cancel states, Axe and viewport reflow; owned-port invalid/same/occupied refusal preserves listener |
| Static/dependency/build |Prettier, ESLint, strict TypeScript, generated OpenAPI declarations, Vite build; full npm audit0known findings |

The local sandbox blocked Mockito's self-attach pipe once; the same unmodified test configuration
passed under the normal user JVM. H2/Flyway's existing newer-H2 warning and Mockito future-JDK
agent warning remain visible. Hosted PostgreSQL, container builds and CodeQL are separate required
checks; local H2/browser success does not substitute for them. No local Docker repair was performed.

## Limits and references

This is single-instance, simulated-provider engineering software. Demo headers are not real
authentication. Keyset pages are live; each older audit request captures new current state. No
audit signature, provider forecast, automatic job execution or production scaling claim is made.
Legacy unbounded audit/account-list APIs remain compatible; the new review endpoint is bounded.
Coverage is evidence of exercised code, not proof of every possible behavior.

- [Workflow guide](../job-review.md), [API](../api.md), [complexity](../complexity.md).
- [Java21 Clock.tick contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/Clock.html#tick(java.time.Clock,java.time.Duration)).
- [PostgreSQL timestamp resolution](https://www.postgresql.org/docs/current/datatype-datetime.html).
- [PostgreSQL transaction isolation](https://www.postgresql.org/docs/current/sql-set-transaction.html).
