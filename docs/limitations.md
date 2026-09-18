# Limitations and next production steps

- Provider adapters are deterministic simulations; there is no OAuth, webhook verification, or
  live-provider contract test.
- The role header is an educational boundary, not authentication or authorization.
- Job claiming is single-instance. Horizontal workers require transactional claim/lease semantics.
- Normalized records are counted but not persisted; a production sink needs page-level idempotency.
- Retries run only when an operator invokes the scheduler endpoint; a production service needs a
  controlled background worker or durable queue.
- H2 accelerates local tests but does not replace PostgreSQL CI and staging validation.
- Prometheus counters and health exist, but alert rules, dashboards, traces, and retention do not.
- Compose credentials are local-only defaults and must be replaced by managed secrets.
- Disaster recovery, zero-downtime migration rehearsal, signed images, and SBOM publication are
  documented production concerns, not claimed features.
