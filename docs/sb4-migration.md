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

## jjwt 0.11.5 → 0.13.0 — `aud` claim modeled as a Set

- jjwt **0.12+** always models the `aud` claim internally as `Set<String>`,
  regardless of whether the JSON wire value is a string or an array.
  Therefore `claims.get("aud", String.class)` now **throws** `RequiredTypeException`.
- **Read side:** read audience via the canonical `Claims.getAudience()`
  (`Set<String>`). Production read site: `TokenGeneratorService`
  (inbound consumer-token `aud`). Test reads: `VerificationSteps#checkPubSub`
  / `#checkAud` / `#checkMultipleAud`.
- **Multiple audiences are now preserved end-to-end.** An earlier interim helper
  (`OauthTokenUtil.getAudience`) collapsed the audience to the first value; it has
  been **removed**. `TokenGeneratorService` was refactored off the old
  `HashMap<String,String>` claims container onto jjwt's native `ClaimsBuilder` /
  `Claims`, so `aud` is set via the typed `.audience().add(Collection).and()`
  builder and a multi-valued consumer-token audience flows through intact.
  - **Precedence (unchanged semantics):** the consumer token's audience(s) win
    when present; otherwise a non-legacy `subscriberId` is the fallback.
    `ClaimsBuilder#audience` is **additive** (it unions, it does not replace), so
    the two branches are expressed as a single exclusive `if/else-if` — a naive
    line-by-line translation of the old `map.put("aud", …)` (which replaces) would
    silently union `subscriberId` with the consumer audiences.
- **Emit wire format is preserved automatically**: jjwt 0.12.4+ restored
  backward-compat — a single-element audience serializes as a JSON **string**,
  multiple as a JSON array. So a single `aud` still emits a string, matching
  pre-migration 0.11.5. No emit change required.
- **Weak-key error mapping moved off the exception type.** The modern
  `Jwts.builder().signWith(key, Jwts.SIG.RS256)` surfaces a too-weak RSA key as a
  `io.jsonwebtoken.security.SignatureException` at sign time — **not** the old
  `WeakKeyException` that the deprecated `signWith(key, SignatureAlgorithm.RS256)`
  threw. Catching `WeakKeyException` therefore silently stopped mapping to 401
  (→ 500). `TokenGeneratorService` now checks RSA modulus bit length up front
  (`RSAKey#getModulus().bitLength() < 2048`) and throws the 401 explicitly,
  independent of jjwt's internal exception type (see the `errorResponse.feature`
  "external IDP weak key configured" scenario + `TokenGeneratorServiceTest`).
- Unsecured-claim parsing changed: jjwt ≥ 0.12 only parses unsecured JWTs whose
  header declares `"alg":"none"`. Reading a signed token's claims without
  verification requires stripping the signature and swapping in an unsecured
   header (see `OauthTokenUtil.getAllClaimsFromToken`).

## Tracing — OpenTelemetry (merged from main) + SB4 module rename

- `main` adopted **OpenTelemetry** (PR #124: `micrometer-tracing-bridge-otel`,
  `opentelemetry-exporter-otlp` + `opentelemetry-exporter-zipkin`,
  `opentelemetry-extension-trace-propagators`). This SB4 branch originally used
  **Brave**. The two were reconciled onto **OTel** when main was merged in.
- **CI-only failure trap**: the SB4 branch forked before PR #124. Merging the PR
  into main produced a pom with *both* Brave and OTel bridges (no textual
  conflict, so git auto-merged silently). With both bridges present Spring Boot
  wires a non-functional tracer → **no spans**: zero `traceId` in logs and empty
  `X-B3-TraceId`/`SpanId` propagated downstream → ~75 cucumber failures (missing
  B3 headers, mis-mapped error responses). Reproduced only in the *merge*, never
  in a plain branch checkout (which had Brave only) — JDK/CPU/test-order were all
  red herrings. Lesson: a branch that touches `pom.xml` tracing deps must be kept
  in sync with main; a clean textual merge can still be a semantic conflict.
- **SB4 module rename**: the actuator tracing auto-configurations moved out of
  `spring-boot-actuator-autoconfigure` into dedicated modules. Use
  `spring-boot-micrometer-tracing-opentelemetry` (the OTel analogue of
  `spring-boot-micrometer-tracing-brave`). Auto-config class names changed too —
  `spring.autoconfigure.exclude` entries had to be updated from the SB3.5 paths
  (`org.springframework.boot.actuate.autoconfigure.tracing.{otlp,zipkin}.*`) to
  the SB4 paths under
  `org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.*`
  (`otlp.OtlpTracingAutoConfiguration`,
  `zipkin.ZipkinWithOpenTelemetryTracingAutoConfiguration`). **Caution:** Spring
  Boot **silently ignores** an excluded class that is not on the classpath (only
  classpath-present non-auto-config classes fail startup). A stale/typo'd exclude
  is therefore a no-op — it does *not* crash, it just leaves *both* exporters
  active. Hence the guard tests below assert the exclusion actually took effect.
- **Exporter toggle is preserved**: both OTLP and Zipkin exporter libs stay on
  the classpath; `TRACING_EXPORTER` (→ `spring.profiles.active`, default
  `zipkin`) activates `application-otlp.yml` / `application-zipkin.yml`, each
  excluding the *other* exporter's auto-config so exactly one is active. Both run
  on the OpenTelemetry tracer (B3 propagation kept). Tests pin to one exporter
  via `application-test.yml` (excludes the SB4 Zipkin auto-config → OTLP active).
- **Guard tests**: `TracingOtlpExporterProfileTest` / `TracingZipkinExporterProfileTest`
  boot the app under `test,otlp` / `test,zipkin` and assert the right tracing
  auto-config bean is present and the other excluded. The real `SpanExporter`
  beans are not instantiated under `@AutoConfigureTracing`, so the auto-config
  *class* beans are asserted instead. These guard against stale/typo'd exclude
  class names (which Spring Boot silently ignores). Note: `spring.autoconfigure.exclude`
  is replace-not-merge across profiles, so the later-activated exporter profile's
  exclude wins over `application-test.yml`'s — both toggle paths resolve correctly.

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

- Pin **all** Jetty artifacts to `12.0.36` via a dedicated
  `wiremock-jetty.version=12.0.36` property plus `jetty-bom` / `jetty-ee10-bom`
  imports. WireMock 3.x (latest 3.13.2) ships/tests against Jetty **12.0.x** only;
  SB4 manages Jetty at 12.1.x, and the 12.0/12.1 mix breaks `ServletContextHandler`.
  Jetty is **test-only** (Jumper runs on Netty), so the pin never ships in the image.
  Kept at the latest 12.0.x patch (`12.0.36`) to clear test-scope Jetty CVEs
  (CVE-2026-1605/CVE-2026-2332/CVE-2025-11143). Only WireMock **4.x** (still beta)
  targets Jetty 12.1; revisit the pin if/when it goes GA.
- **Never** touch SB4's own `jetty.version` property — use the separate
  `wiremock-jetty.version` to align WireMock's embedded Jetty.

## Java 25 runtime — Netty native-access warning (benign, left unaddressed)

On startup in-cluster the JVM logs (once, to stderr):

```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::loadLibrary has been called by io.netty.util.internal.NativeLibraryUtil
         in an unnamed module (file:/app/libs/netty-common-4.2.15.Final.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

- **Cause**: JEP 472 (JDK 24+). Netty loads its native transport (epoll/DNS) via
  `System.loadLibrary`, a *restricted* method. `netty-common` is on the classpath
  → **unnamed module** → no native-access grant → warning. Surfaced purely by the
  JDK 21 → 25 upgrade; nothing functional changed (the library still loads, app
  runs fine).
- **Impact**: cosmetic **today**; a future JDK will *block* restricted methods by
  default, turning this into a hard startup failure unless native access is granted.
- **Who can fix it**: by JEP 472 design, **only the application/deployer** can grant
  native access — a classpath library cannot self-grant. Netty's only lever is
  becoming a real JPMS module (it is currently an *automatic* module,
  `Automatic-Module-Name: io.netty.common`), which would merely let the grant be
  scoped to `io.netty.common` instead of `ALL-UNNAMED`; the flag would still be
  required. Migrating Netty to Panama FFM does **not** remove the need (FFM downcalls
  are restricted too). So this is an application-packaging concern, not a Netty bug.
- **Decision: left unaddressed for now** (warning only). When needed, grant via the
  Jib container launch — `--enable-native-access=ALL-UNNAMED` in
  `jib-maven-plugin` `<container><jvmFlags>` (or `JDK_JAVA_OPTIONS`), or the fat-jar
  manifest attribute `Enable-Native-Access: ALL-UNNAMED`. `ALL-UNNAMED` is correct
  because Netty is classpath (unnamed module), not a named module. Tests don't show
  the warning (different launch path; epoll only loads on Linux).

## Test data / keys

- Weak-key test (`errorResponse.feature`): `RS256` requires keys ≥ 2048 bits;
  jjwt throws `WeakKeyException` ("Key is too weak") for smaller keys.
  `Config.PRIVATE_RSA_KEY_WEAK_EXAMPLE` is a valid **1024-bit PKCS#8** key
  (decodes under JDK 25 but is rejected by `signWith` as too weak — JDK-version
  robust, unlike relying on lenient PKCS#1 parsing).
- `RsaUtils.getPrivateKey` uses `PKCS8EncodedKeySpec` + `KeyFactory("RSA")`.

## Environment / config reference

- Java target = 25 (`<java.version>25</java.version>`; class file v69). Build/runtime
  JVM: Temurin 25.0.1. Runtime base image moved `gcr.io/distroless/java21-debian12`
  → `gcr.io/distroless/java25-debian13:nonroot` (Temurin 25; Distroless dropped the
  debian12 Java line and now ships Java images on debian13 only, LTS 17/21/25).
  CI `JAVA_VERSION` default bumped 21 → 25 in `.github/workflows/build.yml`.
- spring-data-redis resolves to 3.5.7.
- `RedisZoneHealthStatusServiceTest` uses Testcontainers `redis:latest` (Docker);
  autowired `RedisTemplate<String,String>` = `stringRedisTemplate`.
- Mock ports: upstream/`REMOTE_HOST_PORT=1080` (paths `/real`, `/failover`,
  `/provider`), iris `1081`, horizon `1082`. TLS upstream
  `https://localhost:1080` expects 504 (62s delay).
