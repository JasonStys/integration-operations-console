# Validation report

- Recorded: 2026-09-17
- Release candidate: 1.0.0

| Gate                                                          | Result                                           |
| ------------------------------------------------------------- | ------------------------------------------------ |
| Required documentation, local-link, and source-header checker | Passed                                           |
| Generated source declaration/variable index                   | Passed/current                                   |
| OpenAPI-to-TypeScript generation                              | Passed                                           |
| Locked npm install and audit                                  | Passed; 0 known vulnerabilities reported locally |
| Maven toolchain/enforcer, compile, package                    | Passed                                           |
| Flyway V1 migration in PostgreSQL-compatible test database    | Passed                                           |
| Docker Compose configuration model                            | Passed locally                                   |
| Packaged API HTTP smoke test                                  | Passed: health, idempotency, pagination, audit   |
| Container image build                                         | Passed in GitHub Actions                         |
| Real PostgreSQL 18.6 migration and suite                      | Passed in GitHub Actions                         |
| Git diff whitespace check                                     | Passed                                           |
| GitHub Actions CI                                             | [Passed for the database fix][ci-run]            |
| CodeQL for Java and TypeScript                                | [Passed for the database fix][codeql-run]        |

The local Docker daemon was unavailable, so image construction was validated on the hosted Linux
runner. No live provider, external credential, personal dataset, or production system was used.

[ci-run]: https://github.com/JasonStys/integration-operations-console/actions/runs/35302758060
[codeql-run]: https://github.com/JasonStys/integration-operations-console/actions/runs/35302757940
