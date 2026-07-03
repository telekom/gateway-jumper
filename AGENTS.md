# AGENTS.md

Project: `gateway-jumper`.

## Build & Test

- Build: `./mvnw clean package -DskipTests`.
- Tests: `./mvnw clean test`. Single test: `./mvnw clean test -Dtest=X` — the
  leading `clean` is required (incremental `-Dtest=` without `clean` fails Lombok).
- Lombok-related LSP errors (getters, `log`/`@Slf4j`) are spurious until a real
  `mvnw` compile; a clean `mvnw` run is authoritative.
