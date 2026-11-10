# API guide

The canonical machine-readable contract is [openapi.yaml](openapi.yaml). All routes are under
`/api/v1` and use JSON. Errors use `application/problem+json`-shaped fields without stack traces.

## Headers

- `Idempotency-Key`: required by `POST /jobs`; 8–100 characters from letters, digits, `. _ : -`.
- `X-Demo-Role: operator`: required for every state change. This is not authentication.
- `X-Demo-Actor`: optional audit display value, limited to 100 characters.
- `X-Correlation-Id`: accepted when valid and always returned; otherwise the API creates one.

## Routes

| Method and path          | Purpose                                    |
| ------------------------ | ------------------------------------------ |
| `GET /connectors`        | List fictional connector choices           |
| `GET`, `POST /accounts`  | List or idempotently link accounts         |
| `GET`, `POST /jobs`      | Keyset-page or idempotently submit jobs    |
| `GET /jobs/{id}`         | Read one job                               |
| `POST /jobs/run-next`    | Process the earliest eligible job page     |
| `POST /jobs/{id}/run`    | Process a named eligible job page          |
| `POST /jobs/{id}/cancel` | Cancel non-terminal work                   |
| `POST /jobs/{id}/replay` | Requeue dead-lettered work                 |
| `GET /jobs/{id}/audit`   | Read append-only lifecycle evidence        |
| `GET /jobs/{id}/inspection` | Capture current state and a bounded audit window |
| `GET /dashboard`         | Read all status counts and ten recent jobs |

`GET /connectors` returns discovery metadata in ascending, case-sensitive connector-ID order
(Java `String.compareTo`), rather than display-name or hash iteration order. The current choices
are `atlas-ads`, then `beacon-audience`. Discovery reads immutable startup metadata and does not fetch
provider data. This ordering does not grant either provider priority when jobs are scheduled.

## Example

```bash
curl -sS http://localhost:8081/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -H 'X-Demo-Role: operator' \
  --data '{"connectorId":"atlas-ads","displayName":"Demo","externalReference":"demo-1"}'
```

The returned account identifier can be used in `POST /jobs`. Repeating a job request with the same
idempotency key returns the original resource with `created: false`.

The same key must retain the same account, failure plan and maximum attempts; a changed request
returns HTTP 409. A separate sync needs a new key. Actor/correlation metadata can differ on a retry.

`GET /jobs?limit=20&accountId=<uuid>&status=RETRY_WAIT&cursor=<opaque>` accepts independent optional
account and status filters. Limits are 1–100, cursors at most 256 URL-safe characters. New cursors
bind the filters; changing filters with an old cursor returns 400. Original two-field cursors remain
valid only for unfiltered queries. Cursors are navigation state, not access tokens or signed proof.

`GET /jobs/{id}/inspection?limit=25&before=<positive-event-id>` captures the job, service clock and
event window inside a read-only repeatable-read transaction. `history.items` is chronological
within its newest-first window; `history.nextBefore` is an exact decimal string or null. Passing it
requests older events. `nextEligibleDelayMs` is nonnegative for queued/retry-wait jobs and null for
other states. It reports availability at capture, not a provider promise or a live countdown.
Every older-window request captures current state again. The original unbounded audit endpoint
is retained for compatibility; new UI reviews exclusively use the bounded endpoint.
