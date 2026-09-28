# AGENTS.md

Project: `gateway-jumper`.

## Engineering

- Implement changes end to end across callers, configuration, failure behavior,
  tests, documentation, migration, and cleanup. Do not leave parallel or
  half-migrated paths.
- Validate external data at its boundary and represent valid state explicitly
  inside the application.
- Fix root causes rather than adding guards that only hide symptoms.
- Keep responsibilities at their existing boundary. Prefer direct code and
  established repository patterns; add an abstraction only for a concrete need.
- Test observable behavior, including failure paths and integration boundaries.
  A bug fix must include a regression test that fails without the fix.
- Prefer extending Cucumber scenarios for observable gateway behavior. Use
  focused JUnit or Spring Boot tests when behavior sits below that integration
  boundary or cannot be expressed reliably through Cucumber.
- Keep instructions, examples, configuration, schemas, and operational
  documentation consistent with runtime behavior.
- If you fear performance degradation will be introduced by a change, state the
  concerns in your review. Don't make up measurements or state definitive claims
  that you have not verified.

## Build and test

- Build: `./mvnw clean package -DskipTests`.
- Tests: `./mvnw clean test`. Single test: `./mvnw clean test -Dtest=X` — the
  leading `clean` is required (incremental `-Dtest=` without `clean` fails Lombok).
- Lombok-related LSP errors (getters, `log`/`@Slf4j`) are spurious until a real
  `mvnw` compile; a clean `mvnw` run is authoritative.
