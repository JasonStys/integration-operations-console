# Data model

## `connector_accounts`

Stores a fictional provider identity and human-readable name. `(connector_id, external_reference)`
is unique, so repeated linking returns one account.

## `sync_jobs`

Stores the lifecycle state, simulated failure plan, attempts, cursor, next eligible time,
correlation/idempotency identifiers, last safe error, accepted-record count, and timestamps. Check
constraints reject invalid statuses, plans, attempts, and counts. `idempotency_key` is unique.

The scheduler index begins with `(status, available_at, created_at)`. The listing index orders by
`(updated_at DESC, id DESC)`, which matches the encoded keyset cursor and prevents duplicates when
timestamps tie.

## `audit_events`

Stores append-only event type, actor, detail, and occurrence time for a job. `(job_id, occurred_at,
id)` supports ordered history reads. The application exposes insertion and reading only.

Flyway migrations are forward-only. Add `V2__description.sql` for a future change; never rewrite
the applied V1 migration.
