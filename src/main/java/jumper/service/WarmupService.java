// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import jumper.Constants;
import jumper.config.WarmupProperties;
import jumper.health.WarmupHealthIndicator;
import jumper.model.config.JumperConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@ConditionalOnProperty(
    prefix = "jumper.warmup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WarmupService {

  private final WarmupProperties warmupProperties;
  private final WarmupHealthIndicator warmupHealthIndicator;
  private final KeyPair warmupKeyPair;

  public WarmupService(
      WarmupProperties warmupProperties, WarmupHealthIndicator warmupHealthIndicator) {
    this.warmupProperties = warmupProperties;
    this.warmupHealthIndicator = warmupHealthIndicator;
    this.warmupKeyPair = generateEcKeyPair();
  }

  private static KeyPair generateEcKeyPair() {
    try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
      keyGen.initialize(new ECGenParameterSpec("secp256r1"));
      return keyGen.generateKeyPair();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate EC keypair for warmup", e);
    }
  }

  @Value("${server.port:8080}")
  private int serverPort;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    List<String> urls = warmupProperties.getUrls();
    if (urls == null || urls.isEmpty()) {
      log.info("Warmup: no URLs configured, skipping");
      return;
    }

    Duration timeout = warmupProperties.getTimeout();
    log.info("Warmup: starting for {} URL(s) with {}s timeout", urls.size(), timeout.toSeconds());

    long start = System.currentTimeMillis();

    Flux.fromIterable(urls)
        .flatMap(this::warmupUrl)
        .timeout(timeout)
        .onErrorResume(
            throwable -> {
              log.warn("Warmup: aborted due to timeout or error: {}", throwable.getMessage());
              return Mono.empty();
            })
        .doFinally(
            signal -> {
              long elapsed = System.currentTimeMillis() - start;
              log.info("Warmup: completed in {}ms (signal={})", elapsed, signal);
              warmupHealthIndicator.setReady();
            })
        .subscribe();
  }

  private Mono<Void> warmupUrl(String url) {
    log.info("Warmup: warming up {}", url);
    long start = System.currentTimeMillis();

    try {
      String consumerToken = buildSyntheticConsumerToken();
      String jumperConfigBase64 = buildWarmupJumperConfig(url);

      WebClient warmupClient =
          WebClient.builder().baseUrl("http://localhost:" + serverPort).build();

      return warmupClient
          .get()
          .uri(Constants.PROXY_ROOT_PATH_PREFIX + "/warmup")
          .header(Constants.HEADER_JUMPER_CONFIG, jumperConfigBase64)
          .header(Constants.HEADER_AUTHORIZATION, consumerToken)
          .header(Constants.HEADER_REMOTE_API_URL, url)
          .header(Constants.HEADER_API_BASE_PATH, "/")
          .header(Constants.HEADER_REALM, Constants.DEFAULT_REALM)
          .header(Constants.HEADER_ENVIRONMENT, "warmup")
          .accept(MediaType.APPLICATION_JSON)
          .exchangeToMono(response -> response.releaseBody())
          .doOnSuccess(
              v ->
                  log.info("Warmup: {} completed in {}ms", url, System.currentTimeMillis() - start))
          .doOnError(
              throwable ->
                  log.warn(
                      "Warmup: {} failed after {}ms: {}",
                      url,
                      System.currentTimeMillis() - start,
                      throwable.getMessage()))
          .onErrorResume(throwable -> Mono.empty());

    } catch (Exception e) {
      log.error("Warmup: failed to build warmup request for {}: {}", url, e.getMessage());
      return Mono.empty();
    }
  }

  String buildWarmupJumperConfig(String url) {
    JumperConfig jc = new JumperConfig();
    jc.setRemoteApiUrl(url);
    jc.setApiBasePath("/");
    jc.setRealmName(Constants.DEFAULT_REALM);
    jc.setEnvName("warmup");
    // No internalTokenEndpoint, no externalTokenEndpoint → triggers LMS token path
    return JumperConfig.toJsonBase64(jc);
  }

  private String buildSyntheticConsumerToken() {
    HashMap<String, Object> claims = new HashMap<>();
    claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, "warmup");
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, "warmup");
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_ZONE, "warmup");
    claims.put(Constants.TOKEN_CLAIM_SUB, "warmup");
    claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");

    String token =
        Jwts.builder()
            .setClaims(claims)
            .setIssuer("warmup")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 300_000))
            .signWith(warmupKeyPair.getPrivate(), SignatureAlgorithm.ES256)
            .compact();

    return Constants.BEARER + " " + token;
  }
}
