# Test summary

- Recorded: 2026-09-17
- Environment: Windows 11, Eclipse Temurin Java 21.0.12, Maven 3.9.16, Node.js 24.18.1
- Scope: deterministic local verification before initial publication

## Results

| Suite                                      | Result                                                          | Evidence                     |
| ------------------------------------------ | --------------------------------------------------------------- | ---------------------------- |
| Java unit/integration/API/characterization | 22 passed, 0 failed, 0 skipped                                  | Maven Surefire reports       |
| Java coverage gate                         | Passed; 83.27% aggregate line coverage                          | JaCoCo check/report          |
| Java style                                 | Passed; 0 violations                                            | Maven Checkstyle             |
| React and HTTP client                      | 8 passed across 2 test files                                    | Vitest report                |
| Frontend coverage                          | 89.13% lines, 87.75% statements, 90% branches, 80.39% functions | V8 coverage summary          |
| Frontend lint/type/format                  | Passed                                                          | ESLint, TypeScript, Prettier |
| Production web build                       | Passed; 228.84 kB JS (71.53 kB gzip)                            | Vite build output            |
| Performance characterization               | Passed; 250,000 constant-time decisions within guardrail        | JUnit test                   |

The performance case is a regression characterization, not a hardware-neutral benchmark. GitHub
Actions results and uploaded reports are authoritative for each public commit.
