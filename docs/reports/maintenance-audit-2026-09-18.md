# Maintenance audit — 2026-09-18

## Result

The final validated source baseline was `864805e`. Hosted [CI](https://github.com/JasonStys/integration-operations-console/actions/runs/35388432174) and [CodeQL](https://github.com/JasonStys/integration-operations-console/actions/runs/35388432213) passed after the maintenance merge set.

## Corrective work

- Repository dependency-graph support was enabled so pull-request dependency review could run.
- Spring Boot was migrated to 4.1.1 using the modular Web MVC test and Flyway starters and the updated MockMvc annotation package.
- Checkout, setup-node, setup-java, CodeQL, nginx, Maven Enforcer, and JaCoCo updates were merged after their complete checks passed.
- The frontend builder moved to the latest Node 24 Alpine patch after Java, TypeScript, PostgreSQL,
  container, dependency-review, and CodeQL checks passed.
- TypeScript 7 and Node 26 frontend updates were deferred because they contradict the current lint and Node 24 contracts.
- The Java 25 image update was deferred because the build intentionally enforces Java 21.

The final CI run covers Java tests and coverage, TypeScript checks, PostgreSQL migrations, repository policy, containers, and production dependency audit. No open pull request or non-default maintenance branch remained when this report was prepared.
