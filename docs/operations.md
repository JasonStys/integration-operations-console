# Operations runbook

## Start and verify

Run `docker compose up --build`, then confirm:

- Web: `curl -fsS http://localhost:8081/`
- Health: `curl -fsS http://localhost:8081/actuator/health`
- API: `curl -fsS http://localhost:8081/api/v1/connectors`
- Metrics: `curl -fsS http://localhost:8080/actuator/prometheus` when running the API directly

Run `bash scripts/demo.sh` for a complete safe workload.

## Configuration

| Variable                 | Meaning                    | Default                      |
| ------------------------ | -------------------------- | ---------------------------- |
| `IOC_DATABASE_URL`       | JDBC database URL          | In-memory H2 PostgreSQL mode |
| `IOC_DATABASE_USERNAME`  | Database login             | `sa`                         |
| `IOC_DATABASE_PASSWORD`  | Database password          | empty                        |
| `IOC_DATABASE_POOL_SIZE` | Maximum JDBC pool          | `10`                         |
| `IOC_ALLOWED_ORIGIN`     | Browser development origin | `http://localhost:5173`      |
| `IOC_PORT`               | API port                   | `8080`                       |

## Diagnosis

1. Check health and container status with `docker compose ps`.
2. Read bounded recent output with `docker compose logs --tail=200 api db web`.
3. Use the response `X-Correlation-Id` to connect an HTTP failure with its job/audit history.
4. Inspect `/actuator/prometheus` for `operations_jobs_total` outcomes.
5. If a job is delayed, compare `availableAt` with current UTC time; retries are intentionally not
   eligible before that instant.
6. If a job is dead-lettered, inspect `/jobs/{id}/audit` before replaying it.

## Recovery

- Transient or rate-limited work becomes eligible automatically after its scheduled time.
- Permanent/exhausted work requires the explicit replay endpoint and retains prior audit history.
- A bad new container can be rolled back by starting the prior image; Flyway changes must remain
  backward compatible until rollback is no longer required.
- The Compose database is recoverable while its named volume exists. Back up real deployments with
  tested PostgreSQL backup/restore procedures.

## Shutdown

`docker compose down` preserves data. `docker compose down --volumes` permanently removes the local
demonstration database and should be used only intentionally.
