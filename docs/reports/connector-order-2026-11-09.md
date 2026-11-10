# Stable connector discovery — 2026-11-09

Author: Jason Stys. Reviewed baseline: `c2396b7f110798eedd40170ed25460a01d70b1f3`.
This correction extends the [practical workflow report](practical-workflows-2026-11-09.md).

## Confirmed defect and correction

The registry sorted connector IDs into a `LinkedHashMap`, then copied that map with `Map.copyOf`.
Discovery subsequently iterated the copy. The [Java 21 Map contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html#unmodifiable)
and [OpenJDK 21 source](https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/classes/java/util/Map.java)
specify no iteration-order guarantee for these immutable map factories. Sorting the input did
not make that copy's iteration order an API guarantee.

Before the fix, the independent five-connector regression returned `alpha, zulu, mike, BB, Aa`
instead of the explicit expected order `Aa, BB, alpha, mike, zulu`. The defect was reproduced in
the real Java 21 registry, without altering JVM hash seeds or using private implementation hooks.

The immutable hash lookup remains in place. A separate immutable descriptor list is now captured
from the explicitly sorted constructor index. Discovery returns that snapshot in case-sensitive
connector-ID order; it does not fetch provider pages. Registry construction remains O(c log c),
lookup is expected O(1), and returning metadata is O(1), with O(c) JSON serialization and O(c)
registry storage. This adds no scheduling preference or new provider behavior.

## Executed local evidence

- **36 Java tests passed**, with zero failures, errors or skipped tests, through offline Maven
  3.9.16 `verify` on actual Temurin JDK 21.0.12.1+1 using the existing dependency cache.
- Four new registry tests cover all **120 permutations** of five metadata connectors against
  hand-written ID/display pairs, including distinct colliding String IDs `Aa`/`BB`; exact lookup
  identity; duplicate-ID rejection; empty discovery; unknown IDs; caller-input mutation; and
  rejected modification of the returned list. The fixture fails if discovery fetches provider data.
- A new real Spring/MockMvc test verifies that `GET /api/v1/connectors` returns `atlas-ads`, then
  `beacon-audience`, through the actual controller/service/registry stack.
- JaCoCo's unchanged 70% line gate passed: **494/565 lines (87.43%)** overall. The registry itself
  has **16/16 lines and 4/4 branches** covered. Checkstyle reported zero violations.
- Repository documentation/header/link validation, regenerated exact code index and Git whitespace
  checks passed. Frontend code, demo data, API schemas, dependencies and workflow gates are unchanged.

The sandboxed full-suite attempt hit the existing Windows JVM self-attach pipe restriction;
the unmodified test configuration passed outside that restriction. Existing Flyway/newer-H2 and
Mockito future-JDK agent warnings remain visible. Only isolated synthetic H2 data was used.

This agent did not run PostgreSQL, browser, container or hosted Actions checks for this correction,
and did not push or merge it. Those required hosted checks remain the coordinator's publication
gates. The prior workflow/browser evidence remains historical evidence at its recorded source;
this report makes no new production-readiness or live-provider claim.
