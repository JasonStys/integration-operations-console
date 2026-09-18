# Repository map

The root keeps only ownership, build, orchestration, and policy files. Runtime code is split once by
deployable (`backend`, `frontend`) and then by responsibility; documentation has one `adr` and one
`reports` child. This limits path depth without mixing concerns.

```text
.
├── .github/                 CI, security scanning, dependency updates
├── backend/                 Java API, domain, connectors, SQL adapter, tests
│   └── src/
│       ├── main/java/.../   connector, domain, service, store, web
│       ├── main/resources/  configuration and Flyway SQL
│       └── test/java/.../   unit, integration, HTTP, characterization tests
├── frontend/                React/TypeScript UI, HTTP client, tests, Nginx image
├── docs/                    design, contract, runbooks, evidence, generated index
├── scripts/                 one-command verification, demo, repository checks
└── compose.yaml             runnable local topology
```

README's repository guide summarizes every authored file or cohesive file family. The generated
[code index](code-index.md) provides exact declaration and important-variable lines inside source
files, avoiding line numbers in comments that become stale after every edit.
