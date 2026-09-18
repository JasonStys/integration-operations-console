# ADR 0001: Use a modular monolith

- Status: accepted
- Date: 2026-09-17

## Context

The portfolio project must demonstrate meaningful frontend, API, reliability, database, and
operations work while remaining understandable and runnable by one reviewer. Integration job state
needs atomic writes across a job and its audit event.

## Decision

Use one Spring Boot deployment with explicit web, application, domain, connector, and persistence
boundaries, plus a separately built static React client. Keep module dependencies directional and
place transactions in the application service.

## Alternatives considered

- Separate API, scheduler, and connector microservices would demonstrate distributed deployment but
  add queues, tracing, contract coordination, and local setup disproportionate to the problem.
- One unlayered service would be smaller initially but mix transport, SQL, and state transitions,
  weakening tests and future extraction.
- A serverless workflow could model retries well, but local deterministic execution and portability
  would depend on a vendor runtime.

## Consequences

Atomic lifecycle changes are straightforward, local startup is small, and module-focused tests are
fast. Independent component scaling is unavailable. The architecture documents a database-backed
claim/lease seam so a scheduler can be extracted later without rewriting the domain model.
