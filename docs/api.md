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
| `GET /dashboard`         | Read all status counts and ten recent jobs |

## Example

```bash
curl -sS http://localhost:8081/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -H 'X-Demo-Role: operator' \
  --data '{"connectorId":"atlas-ads","displayName":"Demo","externalReference":"demo-1"}'
```

The returned account identifier can be used in `POST /jobs`. Repeating a job request with the same
idempotency key returns the original resource with `created: false`.
