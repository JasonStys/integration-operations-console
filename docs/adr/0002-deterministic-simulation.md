# ADR 0002: Use deterministic provider simulation

- Status: accepted
- Date: 2026-09-17

## Context

Real integrations require credentials, network access, rate-limit consumption, changing fixtures,
and often restrictive terms. Those dependencies make a public portfolio difficult to verify and
unsafe to run automatically.

## Decision

Implement two fictional adapters behind a provider-neutral interface. Each emits stable cursor
pages and accepts an explicit failure plan. Retry jitter is derived from the job identifier rather
than randomness.

## Alternatives considered

- Live third-party sandbox APIs offer realism but require secrets and introduce nondeterminism.
- Static JSON fixtures are repeatable but do not model pagination, typed errors, or rate limiting.
- A general mock server adds an extra service without improving domain coverage for this scope.

## Consequences

Every reviewer and CI run observes the same behavior with no account setup. This does not prove SDK,
OAuth, webhook-signature, or live-provider compatibility; those exclusions are explicit rather than
hidden.
