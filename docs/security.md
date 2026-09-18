# Security model

## Trust boundaries

Browser input is untrusted. Jakarta validation bounds identifiers, names, page limits, attempts, and
idempotency keys. JDBC operations use parameters rather than SQL concatenation. Unknown JSON fields
are rejected. API errors omit stack traces and database detail.

The browser and API contain no provider secrets because all providers are fictional. Images run as
non-root users, Nginx emits restrictive browser headers, and Compose keeps PostgreSQL off host ports.
GitHub workflows grant read-only repository contents and pin third-party actions to commits.

## Demonstration-only controls

`X-Demo-Role` illustrates where authorization is enforced. Any caller can forge it, so deployment to
an untrusted network would require OIDC/OAuth authentication, server-side roles/scopes, CSRF policy,
rate limiting, TLS, secret management, and audited administrator access.

## Data and logs

Use invented account references only. Correlation identifiers, errors, actors, and audit detail must
not contain credentials or sensitive records. The current fake connector payloads are generated and
non-personal.

## Supply chain

Lockfiles and the Maven wrapper make inputs repeatable. Dependabot monitors Maven, npm, Docker, and
GitHub Actions; CI runs tests, linters, compilers, coverage, and CodeQL. Container base digests are
not pinned in this educational repo so automated patch updates remain straightforward; a release
pipeline should resolve and sign immutable images and emit an SBOM.
