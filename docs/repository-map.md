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

## Native application files

| File or family                                                             | Responsibility                                                                               |
| -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `backend/.../desktop/DesktopProfile.java`                                  | Durable OS-local settings, database URL and exclusive process ownership.                     |
| `backend/.../desktop/DesktopState.java`                                    | Per-launch session, source-bound readiness, native window and database-before-lock shutdown. |
| `backend/.../desktop/DesktopLauncher.java`                                 | Real Swing status/open/quit window and owned loopback Spring service.                        |
| `backend/.../desktop/DesktopBoundaryFilter.java`, `DesktopController.java` | Native-only Host/Origin/session/browser policy and safe help/readiness/quit endpoints.       |
| `application-desktop.yml`                                                  | Packaged defaults without changing developer/external-database configuration.                |
| `frontend/src/desktop.ts`, `DesktopControls.tsx`                           | Origin-bound launch session and user-facing native help/quit.                                |
| `backend/.../desktop/*Test.java`, frontend desktop tests                   | Physical profile retention, boundary/state/lifecycle and browser-session regression cases.   |
| `scripts/build-native.ps1`                                                 | Fresh unsigned Windows app-image with bundled Java and exact source metadata.                |
| `scripts/native-acceptance.mjs`                                            | Actual packaged EXE/Chromium job, failure, export and durable-restart acceptance.            |
| `.github/workflows/native-application.yml`                                 | Read-only source verification and packaged Windows acceptance; no publishing.                |
| `docs/native-application.md`, `native-notices.md`                          | End-user operation/backup boundaries and bundled notices.                                    |

## Reviewed documentation editions

| File                                               | Responsibility                                                                        |
| -------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `docs/handbook.md`                                 | Organized current usage, architecture, security and validation documentation.         |
| `docs/reports/project-guide.docx`                  | Reviewed Word guide bound to the cited source revision.                               |
| `docs/reports/publications.json`                   | Exact Office byte identities, approved visual scopes and immutable evidence baseline. |
| `docs/reports/documentation-refresh-2026-11-09.md` | Dated documentation integration checks and interpretation limits.                     |
