# Spring Boot 4 Migration — Lessons & Notes

Migration of `gateway-jumper` from Spring Boot 3.5.13 to **Spring Boot 4.1.0**
(Spring Cloud 2025.1.2 / Spring Cloud Gateway 5.0.2), including replacing
MockServer with WireMock. Status: complete, full suite green
(`./mvnw clean test` → 278 tests, 0 failures, 0 errors).

## Build & test workflow

- Build/verify: `./mvnw clean package -DskipTests`.
- Full tests: `./mvnw clean test` (cucumber run alone ≈ 2.5 min).
- Single test: `./mvnw clean test -Dtest=X`. The leading `clean` is **required** —
  an incremental `-Dtest=` without `clean` fails Lombok annotation processing.
- LSP errors about Lombok getters/`log`/`@Slf4j` are **spurious** until a real
  `mvnw` compile. Treat a clean `mvnw` run as authoritative, not the editor LSP.
- Surefire reports under `target/surefire-reports/*.txt` are read-blocked/binary
  (use `grep -a`). Service logs are JSON to stdout.

## CI-only cucumber failures — shared-JVM global-state pollution

- Symptom: green locally, red on CI (GitHub `ubuntu-24.04`, Zulu 21). ~75/138
  cucumber scenarios failed — downstream `X-B3-*` trace headers missing on
  proxied requests, and error responses mis-mapped (e.g. `401`→`204`).
- **Not** a JDK issue: reproduced the exact CI JDK locally via
  `sdk install java 21.0.11.crac-zulu` (Zulu21.50+19-CRaC, build 21.0.11+10) →
  full suite green. Also not a stale-cache issue (CI fully recompiles) and not
  reproducible under no-`clean`, 1/2-CPU `taskset`, or full-suite runs locally.
- Root cause: surefire defaults to `forkCount=1, reuseForks=true`, so **all** test
  classes share one JVM, and the default run order is `filesystem`
  (non-deterministic, differs CI vs local). The app leans on process-global
  singletons — the static `ObjectMapperUtil` holder, brave `Tracing.current()`,
  reactor `Hooks`, BlockHound (installed by `RunCucumberTest.@BeforeAll`). Under
  CI's class order the cucumber suite inherited poisoned global tracing state.
  `RunCucumberTest` passes in isolation (`-Dtest=RunCucumberTest`).
- Proof of the coupling: `-DreuseForks=false` breaks 22 pure unit tests with
  `ObjectMapperUtil.getInstance()` == null — those tests silently depend on an
  earlier `@SpringBootTest` populating that static in the same JVM.
- Fix: run `RunCucumberTest` in its **own** surefire execution (`cucumber-test`)
  so it gets a fresh JVM with pristine global state, independent of run order;
  the other tests keep their shared JVM (so the `ObjectMapperUtil` static stays
  populated by a `@SpringBootTest` for the unit tests that need it). See the
  `maven-surefire-plugin` `<executions>` in `pom.xml`. Result: two executions,
  `140` + `138` tests.

## jjwt 0.11.5 → 0.13.0 — `aud` claim modeled as a Set

- jjwt **0.12+** always models the `aud` claim internally as `Set<String>`,
  regardless of whether the JSON wire value is a string or an array.
  Therefore `claims.get("aud", String.class)` now **throws** `RequiredTypeException`.
- This was a **read-side** bug only. Fix: read audience via the canonical
  `Claims.getAudience()` and take the single value.
  - Helper: `OauthTokenUtil.getAudience(Claims)`.
  - Production read site: `TokenGeneratorService` (inbound consumer-token `aud`).
  - Test reads: `VerificationSteps#checkPubSub` / `#checkAud`.
- **Emit wire format is preserved automatically**: jjwt 0.12.4+ restored
  backward-compat — a single-element audience serializes as a JSON **string**,
  multiple as a JSON array. So putting a single `aud` String via `setClaims`
  still emits a string, matching pre-migration 0.11.5. No emit change required.
- Unsecured-claim parsing changed: jjwt ≥ 0.12 only parses unsecured JWTs whose
  header declares `"alg":"none"`. Reading a signed token's claims without
  verification requires stripping the signature and swapping in an unsecured
  header (see `OauthTokenUtil.getAllClaimsFromToken`).

## Spring Cloud Gateway 5.0 — `trusted-proxies`

- New SCG 5.0 security control (remediation for `Forwarded`/`X-Forwarded-*`
  spoofing). Did not exist in SCG 4.x, so nothing needed configuring before.
- New default: when `trusted-proxies` is **unset**, SCG treats the immediate peer
  as untrusted and **strips inbound `Forwarded`/`X-Forwarded-*` headers**.
  Pre-migration SCG passed them through unconditionally.
- Test config (`application-test.yml`) sets `trusted-proxies: ".*"` to restore the
  old pass-through so behavior matches the SB3.5 baseline.
- **Production: intentionally left unset** (most secure). Jumper does not depend
  on inbound forwarded headers — `HeaderUtil.rewriteXForwardedHeader` builds
  `X-Forwarded-Host` from `JumperConfig.consumerOriginStargate` and sets
  port/proto to fixed constants; `application.yml` already disables all gateway
  forwarded handling (`forward-headers-strategy: none`, `forwarded.enabled: false`,
  all `x-forwarded.*: false`). If inbound pass-through is ever required, set the
  trusted ingress CIDR — never `.*` — in production.

## Jackson 2 → Jackson 3 migration

App code is fully migrated to **Jackson 3** (`tools.jackson`). SB4 auto-configures
a Jackson 3 `ObjectMapper`; the only remaining Jackson 2 (`com.fasterxml.jackson`)
on the classpath is transitive via `jjwt-jackson` (jjwt's own backend, runtime).

- **API mapping**:
  - `com.fasterxml.jackson.databind.ObjectMapper` → `tools.jackson.databind.ObjectMapper`.
  - No public no-arg `ObjectMapper()` ctor — build via
    `tools.jackson.databind.json.JsonMapper.builder()...build()`.
  - `com.fasterxml.jackson.core.type.TypeReference` →
    `tools.jackson.core.type.TypeReference`.
  - `DeserializationFeature` → `tools.jackson.databind.DeserializationFeature`.
- **Exceptions are now unchecked**: `readValue`/`writeValueAsString` throw
  `tools.jackson.core.JacksonException` (unchecked). All former
  `catch (JsonProcessingException e)` / `catch (IOException e)` on Jackson calls
  became unreachable and were changed to `catch (JacksonException e)`; `throws
  JsonProcessingException` declarations were dropped.
- **Defaults changed**: `FAIL_ON_UNKNOWN_PROPERTIES` defaults to **false** in
  Jackson 3, so explicit `.configure(FAIL_ON_UNKNOWN_PROPERTIES, false)` calls
  were dropped. JDK8 types (`Optional`, etc.) are **built into** Jackson 3 core —
  no module registration needed, so `jackson-datatype-jdk8` was removed from
  `pom.xml` and `Jdk8Module` is gone from `ObjectMapperUtil`.
- **Annotations are unchanged**: `com.fasterxml.jackson.annotation.*`
  (`@JsonProperty`, `@JsonInclude`, `@JsonIgnore`) is the same package in
  Jackson 3 — these imports were intentionally **not** touched
  (`Spectre`, `SpectreData`, `TokenInfo`, `JumperConfig`).
- **Bean vs static usage / single global mapper**: there is now exactly one
  Jackson 3 `ObjectMapper` — the Spring auto-configured bean. Spring-managed
  beans inject it directly (`SpectreService`, `SpectreBodyRewrite`,
  `RedisZoneHealthStatusService`). `ObjectMapperUtil` is a `@Component` that
  captures that same autowired bean into a static field, so static/non-bean call
  sites (`JumperConfig` static methods, `JumperInfoResponse.toString`,
  `OauthTokenUtil`) reach it via `ObjectMapperUtil.getInstance()` instead of
  building their own mappers. The eager component is constructed during context
  refresh; every `getInstance()` call site runs only during request handling or
  on `ApplicationReadyEvent` (warmup), so the static field is always populated
  first — no null-ordering risk, no defensive fallback needed.
- **Redis**: the old explicit Jackson 2 `@Bean ObjectMapper objectMapper()` in
  `RedisConfig` was **removed** (SB4 supplies the Jackson 3 bean). Redis pub/sub
  deserialization still requires `ZoneHealthMessage` to carry `@NoArgsConstructor`
  alongside `@AllArgsConstructor`.
- LSP reports `tools.jackson` types as unresolved (plus spurious Lombok errors)
  because the editor project model lags the new dependency — a clean `mvnw`
  compile is authoritative.

## WireMock (replacing MockServer)

- WireMock 3.13.2. Server options:
  `options().port(p).gzipDisabled(true).extensions(...)`.
- **`gzipDisabled(true)` on ALL WireMock servers** is required — it resolved a
  batch of payload-comparison failures caused by gzipped response bodies.
- Drop-connection / empty response: `Fault.EMPTY_RESPONSE`.
- Verification: `verify(exactly(n), postRequestedFor(...))`, `findAll(...)`.
- `TestExpectationCallback` reimplemented as a `ResponseDefinitionTransformerV2`.
- Horizon event verification (`MockHorizonServer.createVerifyEventType`) uses
  `retrieveAllEvents(minCount)` and intentionally does **not** filter by trace id
  (autoevents use a jumper-generated trace id); per-scenario `resetAll()` provides
  isolation (`@After("@horizon")`). It is **order-independent**: WireMock's
  `findAll` ordering is not guaranteed, so instead of positional `get(0)` it maps
  every recorded event to its `getType()` and asserts the collection contains the
  adjusted type `de.telekom.ei.listener.spectre`. (Positional access caused a
  JVM-dependent flake — green on JDK 25, one deterministic failure on JDK 21/CI.)
- Cucumber runner: `@SelectClasspathResource("features")` (harmless discovery
  warning suggesting the package selector — cosmetic only).

## Jetty version pinning

- Pin **all** Jetty artifacts to `12.0.30` via a dedicated
  `wiremock-jetty.version=12.0.30` property plus `jetty-bom` / `jetty-ee10-bom`
  imports.
- **Never** touch SB4's own `jetty.version` property — use the separate
  `wiremock-jetty.version` to align WireMock's embedded Jetty.

## Test data / keys

- Weak-key test (`errorResponse.feature`): `RS256` requires keys ≥ 2048 bits;
  jjwt throws `WeakKeyException` ("Key is too weak") for smaller keys.
  `Config.PRIVATE_RSA_KEY_WEAK_EXAMPLE` is a valid **1024-bit PKCS#8** key
  (decodes under JDK 25 but is rejected by `signWith` as too weak — JDK-version
  robust, unlike relying on lenient PKCS#1 parsing).
- `RsaUtils.getPrivateKey` uses `PKCS8EncodedKeySpec` + `KeyFactory("RSA")`.

## Environment / config reference

- Java target = 21 (pom unchanged). Build/runtime JVM observed: Temurin 25.0.1.
- spring-data-redis resolves to 3.5.7.
- `RedisZoneHealthStatusServiceTest` uses Testcontainers `redis:latest` (Docker);
  autowired `RedisTemplate<String,String>` = `stringRedisTemplate`.
- Mock ports: upstream/`REMOTE_HOST_PORT=1080` (paths `/real`, `/failover`,
  `/provider`), iris `1081`, horizon `1082`. TLS upstream
  `https://localhost:1080` expects 504 (62s delay).
