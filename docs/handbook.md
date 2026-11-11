# Integration Operations Console handbook

By Jason Stys.

Version 1.1.0 adds the [bundled Windows application](native-application.md), with a
real status/quit window, local browser session and durable workspace. The original
reviewed Word edition below retains its cited historical source baseline; it does
not automatically incorporate this later source change. The current observed
native evidence is in [the dated report](reports/native-application-2026-11-10.md).

Inspect account-scoped integration jobs, retry scheduling, idempotent submissions and audit history through a Java API and TypeScript interface. The demonstration actor headers are not a production identity provider.

Search all jobs by account/state, page captured audit history and download exact reviews. Fixed request-key intent, real-client binding, timestamp precision, null wire fields and mobile overflow.

Makes integration support usable beyond the recent-activity feed and catches defects through real API/browser workflows.

## Read the documentation

| Edition                                          | Audience and contents                                                                       |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| [project-guide.docx](reports/project-guide.docx) | User and developer guide: purpose, current usage, design, boundaries and source references. |

The editable Markdown remains the maintenance source. The Word files are readable editions. See [publication identities and review](reports/publications.json) and the [documentation refresh record](reports/documentation-refresh-2026-11-09.md).

## Current usage and help

[README quick start](../README.md) is the entry point. Use the project-specific guides below before changing inputs, operating modes or deployment assumptions.

### Usage and operations

- [job review](job-review.md)
- [operations](operations.md)

### Design and contracts

- [adr/0001 modular monolith](adr/0001-modular-monolith.md)
- [adr/0002 deterministic simulation](adr/0002-deterministic-simulation.md)
- [api](api.md)
- [architecture](architecture.md)
- [complexity](complexity.md)
- [data model](data-model.md)

### Security and validation

- [security](security.md)
- [testing](testing.md)

## Historical results and reproduction

The reviewed source is [`2563b4154d2e7755516d12911d68c4a32d654905`](https://github.com/JasonStys/integration-operations-console/tree/2563b4154d2e7755516d12911d68c4a32d654905). The observed functional workflow baseline is `2563b4154d2e7755516d12911d68c4a32d654905`; those observations are recorded at `2026-11-10T05:25:40.628667+00:00`. They do not certify an unrun later revision.

- [Detailed recorded validation](reports/connector-order-2026-11-09.md)
- [Current Actions](https://github.com/JasonStys/integration-operations-console/actions) records later integration checks separately.
- Preserve dated measurements and follow the existing testing guide to reproduce them; a test-source inventory alone does not establish execution.

## Practical boundaries

- Fictional-provider single-instance lab; its demo identity header is not production authentication.
- Keyset pages are live and later audit requests capture new state, not an immutable historical reconstruction.
- Legacy account/audit APIs remain unbounded for compatibility.
- Discovery order is case-sensitive connector-ID order, not display-name order or scheduling priority; registry metadata is captured at construction.

## Maintaining these editions

Update the editable usage/design/test documents first. Rebuild and visually review every Word page and workbook tab before replacing an edition; verify the exact binary digests and evidence source in publications.json. Keep account data, logs with secrets, installed-app state and private test stores out of documentation. Source and release assets retain their existing security and licensing boundaries.
