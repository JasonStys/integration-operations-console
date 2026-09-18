# Architecture

## Goals

The system makes integration failure behavior visible and testable while remaining safe to clone
and run. It favors deterministic demonstrations, explicit state, durable invariants, and narrow
module boundaries over infrastructure volume.

## Components

The React/TypeScript client renders dashboard counts, account and job forms, job actions, and audit
history. Its data types are generated from `openapi.yaml`, reducing contract drift.

The Spring Boot API has five layers:

1. `web` validates transport input, attaches correlation IDs, and maps safe problem responses.
2. `service` owns use cases and transaction boundaries.
3. `domain` owns immutable job state and transition invariants.
4. `connector` contains a provider-neutral port and deterministic fake adapters.
5. `store` provides a persistence port and JDBC implementation.

Flyway owns relational evolution. H2's PostgreSQL mode makes fast tests possible; Compose and CI use
PostgreSQL to validate the production-shaped path.

## Request and job flow

```text
POST /jobs + idempotency key
  -> validate account and request
  -> unique-key insert or return prior job
  -> append JOB_SUBMITTED audit event
  -> scheduler claims eligible work
  -> connector returns page or typed failure
     -> next page: QUEUED
     -> final page: SUCCEEDED
     -> transient/rate limit: RETRY_WAIT or DEAD_LETTERED
     -> permanent: DEAD_LETTERED
```

One process serializes claims with a synchronized service boundary. This is correct for the
single-instance demonstration. A multi-instance implementation would use a database claim query
such as `SELECT ... FOR UPDATE SKIP LOCKED`, record lease ownership, and make every page write
idempotent.

## Reliability boundaries

- Unique database constraints are the final defense against duplicate account links and jobs.
- Retry delay is deterministic for reproducible tests while still spreading jobs by identifier.
- A cursor and accepted-record count are stored with each job so page progress survives restarts.
- Audit events are append-only; application code has no update or delete path for them.
- Connector exceptions are typed as rate-limited, transient, or permanent so policy is explicit.
- No simulated provider state or credential crosses the process boundary.

## Observability

Every response includes a correlation identifier. Job audit events retain actor, event, detail, and
time. Micrometer exposes lifecycle counters through `/actuator/prometheus`; Kubernetes-compatible
liveness/readiness health details are available through Actuator without exposing internals.

## Decisions

- [ADR 0001: modular monolith](adr/0001-modular-monolith.md)
- [ADR 0002: deterministic provider simulation](adr/0002-deterministic-simulation.md)
