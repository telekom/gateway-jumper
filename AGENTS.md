# AGENTS.md

Project: `gateway-jumper`.

## Documentation & Notes

- **Spring Boot 4 migration** (SB 3.5 → 4.1, Spring Cloud 2025.1 / Gateway 5.0,
  MockServer → WireMock): lessons, gotchas, and rationale are tracked in
  [`docs/sb4-migration.md`](docs/sb4-migration.md). Read and update that file for
  anything sb4-migration related (jjwt `aud`-as-Set, Jackson 2/3 coexistence,
  WireMock + `gzipDisabled`, Jetty pinning, SCG `trusted-proxies`, build/test
  workflow quirks).

## Build & Test

- Build: `./mvnw clean package -DskipTests`.
- Tests: `./mvnw clean test`. Single test: `./mvnw clean test -Dtest=X` — the
  leading `clean` is required (incremental `-Dtest=` without `clean` fails Lombok).
- Lombok-related LSP errors (getters, `log`/`@Slf4j`) are spurious until a real
  `mvnw` compile; a clean `mvnw` run is authoritative.
