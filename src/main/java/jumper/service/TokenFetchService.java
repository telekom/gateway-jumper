// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static jumper.Constants.TOKEN_REQUEST_METHOD_POST;
import static jumper.service.TokenFetchMetrics.Mode.BACKGROUND;
import static jumper.service.TokenFetchMetrics.Mode.FOREGROUND;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import jumper.Constants;
import jumper.config.OauthTokenFetchProperties;
import jumper.exception.TokenFetchUnavailableException;
import jumper.model.TokenInfo;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.service.TokenCacheService.FetchSelection;
import jumper.service.TokenFetchMetrics.Mode;
import jumper.service.TokenFetchMetrics.Outcome;
import jumper.util.BasicAuthUtil;
import jumper.util.OauthTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

@Service
@Slf4j
public class TokenFetchService {

  @Qualifier("oauthTokenUtilWebClient")
  private final WebClient oauthTokenUtilWebClient;

  private final TokenCacheService tokenCache;
  private final TokenGeneratorService tokenGeneratorService;
  private final OauthTokenFetchProperties tokenFetchProperties;
  private final TokenFetchMetrics metrics;
  private final Cache<String, Boolean> recentBackgroundRefreshAttempts;

  @Autowired
  public TokenFetchService(
      @Qualifier("oauthTokenUtilWebClient") WebClient oauthTokenUtilWebClient,
      TokenCacheService tokenCache,
      TokenGeneratorService tokenGeneratorService,
      TokenFetchMetrics metrics,
      OauthTokenFetchProperties tokenFetchProperties) {
    this(
        oauthTokenUtilWebClient,
        tokenCache,
        tokenGeneratorService,
        metrics,
        tokenFetchProperties,
        Ticker.systemTicker());
  }

  TokenFetchService(
      WebClient oauthTokenUtilWebClient,
      TokenCacheService tokenCache,
      TokenGeneratorService tokenGeneratorService,
      TokenFetchMetrics metrics,
      OauthTokenFetchProperties tokenFetchProperties,
      Ticker ticker) {
    this.oauthTokenUtilWebClient = oauthTokenUtilWebClient;
    this.tokenCache = tokenCache;
    this.tokenGeneratorService = tokenGeneratorService;
    this.tokenFetchProperties = tokenFetchProperties;
    this.metrics = metrics;
    this.recentBackgroundRefreshAttempts =
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(tokenFetchProperties.minimumBackgroundRefreshInterval())
            .ticker(ticker)
            .build();
  }

  public Mono<TokenInfo> getInternalMeshAccessToken(JumperConfig jc) {
    return getAccessTokenWithClientCredentials(
        jc.getInternalTokenEndpoint() + Constants.ISSUER_SUFFIX,
        jc.getClientId(),
        jc.getClientSecret(),
        null);
  }

  public Mono<TokenInfo> getAccessTokenWithClientCredentials(
      String tokenEndpoint, String clientID, String clientSecret, String scope) {

    final String tokenKey =
        tokenCache.generateTokenCacheKey(tokenEndpoint, clientID, clientSecret, scope);

    return Mono.defer(
        () ->
            resolveToken(
                tokenEndpoint,
                tokenKey,
                () -> createClientCredentialsRequest(clientID, clientSecret, scope)));
  }

  public Mono<TokenInfo> getAccessTokenWithOauthCredentialsObject(
      String tokenEndpoint, OauthCredentials oauthCredentials) {

    final String tokenKey = tokenCache.generateTokenCacheKey(tokenEndpoint, oauthCredentials);

    return Mono.defer(
        () ->
            resolveToken(
                tokenEndpoint,
                tokenKey,
                () -> createOauthCredentialsRequest(tokenEndpoint, oauthCredentials)));
  }

  private Mono<TokenInfo> resolveToken(
      String tokenEndpoint, String tokenKey, Supplier<TokenRequest> requestSupplier) {
    TokenCacheService.TokenLookup cached = tokenCache.lookup(tokenKey);
    if (cached.servable()) {
      metrics.record(Outcome.CACHE_HIT, FOREGROUND);
      if (cached.needsRefresh()) {
        tryStartBackgroundRefresh(tokenEndpoint, tokenKey, requestSupplier);
      }
      return Mono.just(cached.token());
    }

    return getOrCreateInFlightRequest(tokenEndpoint, tokenKey, requestSupplier);
  }

  private TokenRequest createClientCredentialsRequest(
      String clientID, String clientSecret, String scope) {
    MultiValueMap<String, String> requestParameter = new LinkedMultiValueMap<>();
    requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ID, clientID);
    requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_CLIENT_SECRET, clientSecret);
    requestParameter.add(
        Constants.TOKEN_REQUEST_PARAMETER_GRANT_TYPE,
        AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());

    if (StringUtils.isNotBlank(scope)) {
      requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_SCOPE, scope);
    }

    return new TokenRequest(requestParameter, null);
  }

  private TokenRequest createOauthCredentialsRequest(
      String tokenEndpoint, OauthCredentials oauthCredentials) {
    MultiValueMap<String, String> requestParameter = new LinkedMultiValueMap<>();
    String basicAuth = null;

    if (StringUtils.isNotBlank(oauthCredentials.getClientKey())) {
      requestParameter.add(
          Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ID, oauthCredentials.getClientId());
      requestParameter.add(
          Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ASSERTION,
          createJwtTokenForExternalIdp(tokenEndpoint, oauthCredentials));
      requestParameter.add(
          Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ASSERTION_TYPE,
          Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ASSERTION_TYPE_JWT);
    }

    if (StringUtils.isNotBlank(oauthCredentials.getClientId())
        && StringUtils.isNotBlank(oauthCredentials.getClientSecret())) {
      if (StringUtils.isNotBlank(oauthCredentials.getTokenRequest())
          && oauthCredentials.getTokenRequest().equalsIgnoreCase(TOKEN_REQUEST_METHOD_POST)) {
        requestParameter.add(
            Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ID, oauthCredentials.getClientId());
        requestParameter.add(
            Constants.TOKEN_REQUEST_PARAMETER_CLIENT_SECRET, oauthCredentials.getClientSecret());
      } else {
        basicAuth =
            BasicAuthUtil.encodeBasicAuth(
                oauthCredentials.getClientId(), oauthCredentials.getClientSecret());
      }
    }

    if (StringUtils.isNotBlank(oauthCredentials.getUsername())
        && StringUtils.isNotBlank(oauthCredentials.getPassword())) {
      requestParameter.add(
          Constants.TOKEN_REQUEST_PARAMETER_USERNAME, oauthCredentials.getUsername());
      requestParameter.add(
          Constants.TOKEN_REQUEST_PARAMETER_PASSWORD, oauthCredentials.getPassword());
    }

    if (StringUtils.isNotBlank(oauthCredentials.getRefreshToken())) {
      requestParameter.add(
          Constants.TOKEN_REQUEST_PARAMETER_REFRESH_TOKEN, oauthCredentials.getRefreshToken());
    }

    if (StringUtils.isNotEmpty(oauthCredentials.getScopes())) {
      requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_SCOPE, oauthCredentials.getScopes());
    }

    requestParameter.add(
        Constants.TOKEN_REQUEST_PARAMETER_GRANT_TYPE, oauthCredentials.getGrantType());
    return new TokenRequest(requestParameter, basicAuth);
  }

  /**
   * Starts a background refresh unless this token key is still in its refresh cooldown.
   *
   * <p>The cooldown starts when this service creates the shared refresh request and restarts when
   * the request terminates, regardless of its outcome. Foreground token fetches do not use this
   * gate.
   */
  private void tryStartBackgroundRefresh(
      String tokenEndpoint, String tokenKey, Supplier<TokenRequest> requestSupplier) {
    if (recentBackgroundRefreshAttempts.getIfPresent(tokenKey) != null) {
      return;
    }

    FetchSelection refresh =
        getOrCreateSharedRequest(tokenEndpoint, tokenKey, requestSupplier, BACKGROUND);

    if (!refresh.created()) {
      return;
    }

    recentBackgroundRefreshAttempts.put(tokenKey, Boolean.TRUE);
    refresh
        .publisher()
        .subscribe(
            ignored -> {}, error -> recordBackgroundRefreshFailure(tokenEndpoint, tokenKey, error));
  }

  private void recordBackgroundRefreshFailure(
      String tokenEndpoint, String tokenKey, Throwable error) {
    metrics.record(Outcome.BACKGROUND_REFRESH_FAILURE, BACKGROUND);
    log.warn(
        "Background token refresh failed for endpoint {}: {}: {}",
        tokenEndpoint,
        error.getClass().getSimpleName(),
        error.getMessage());
    log.debug("Background token refresh failure details", error);
  }

  private String createJwtTokenForExternalIdp(
      String tokenEndpoint, OauthCredentials oauthCredentials) {
    /*
     * iss - REQUIRED. Issuer. This MUST contain the client_id of the OAuth Client.
     * sub - REQUIRED. Subject. This MUST contain the client_id of the OAuth Client.
     * aud - REQUIRED. Audience. The aud (audience) Claim. Value that identifies the
     * Authorization Server as an intended audience. The Authorization Server MUST
     * verify that it is an intended audience for the token. The Audience SHOULD be
     * the URL of the Authorization Server's Token Endpoint.
     * jti - REQUIRED. JWT ID. A unique identifier for the token, which can be used
     * to prevent reuse of the token. These tokens MUST only be used once, unless
     * conditions for reuse were negotiated between the parties; any such
     * negotiation is beyond the scope of this specification.
     * exp - REQUIRED. Expiration time on or after which the JWT MUST NOT be
     * accepted for processing.
     * iat - OPTIONAL. Time at which the JWT was issued.
     */
    Claims claims =
        Jwts.claims()
            .subject(oauthCredentials.getClientId())
            .audience()
            .add(tokenEndpoint)
            .and()
            .id(UUID.randomUUID().toString())
            .build();

    return tokenGeneratorService.createJwtTokenFromKey(
        claims,
        oauthCredentials.getClientId(),
        new Date(System.currentTimeMillis() + 60 * 1000),
        new Date(System.currentTimeMillis()),
        oauthCredentials.getClientKey());
  }

  private Mono<TokenInfo> getOrCreateInFlightRequest(
      String tokenEndpoint, String tokenKey, Supplier<TokenRequest> requestSupplier) {
    return Mono.defer(
        () -> {
          Mono<TokenInfo> waiter =
              getOrCreateSharedRequest(tokenEndpoint, tokenKey, requestSupplier, FOREGROUND)
                  .publisher();
          if (tokenFetchProperties
                  .requestWaitTimeout()
                  .compareTo(tokenFetchProperties.overallTimeout())
              < 0) {
            waiter =
                waiter
                    .timeout(tokenFetchProperties.requestWaitTimeout())
                    .onErrorMap(
                        TimeoutException.class,
                        error -> {
                          metrics.record(Outcome.DEADLINE, FOREGROUND);
                          return new ResponseStatusException(
                              HttpStatus.GATEWAY_TIMEOUT,
                              "Timed out waiting for a token from " + tokenEndpoint,
                              error);
                        });
          }
          metrics.waiterStarted();
          return waiter
              .onErrorMap(TokenRequestBuildException.class, Throwable::getCause)
              .doFinally(signal -> metrics.waiterFinished());
        });
  }

  /**
   * Atomically joins the current per-key fetch or installs a new cached publisher. Eviction removes
   * the selected publisher, allowing the next caller to install a replacement.
   */
  private FetchSelection getOrCreateSharedRequest(
      String tokenEndpoint, String tokenKey, Supplier<TokenRequest> requestSupplier, Mode mode) {
    Mono<TokenInfo> candidate =
        createInFlightRequest(tokenEndpoint, tokenKey, requestSupplier, mode);
    return tokenCache.getOrCreateFetch(tokenKey, candidate);
  }

  /**
   * Builds a shared fetch that downstream waiter cancellation cannot stop. Terminal cleanup runs
   * before {@code cache()} replays signals to waiters; {@code doFinally} is the guarded fallback
   * for cancellation paths that do not invoke {@code doOnTerminate}.
   */
  private Mono<TokenInfo> createInFlightRequest(
      String tokenEndpoint, String tokenKey, Supplier<TokenRequest> requestSupplier, Mode mode) {
    AtomicReference<Mono<TokenInfo>> self = new AtomicReference<>();
    AtomicBoolean cleanedUp = new AtomicBoolean();
    Runnable cleanup =
        () -> {
          if (cleanedUp.compareAndSet(false, true)) {
            if (mode == BACKGROUND) {
              recentBackgroundRefreshAttempts.put(tokenKey, Boolean.TRUE);
            }
            tokenCache.completeFetch(tokenKey, self.get());
            metrics.fetchFinished();
          }
        };
    Mono<TokenInfo> request =
        Mono.fromCallable(requestSupplier::get)
            .onErrorMap(TokenRequestBuildException::new)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(
                tokenRequest ->
                    getAccessTokenQuery(
                        tokenEndpoint,
                        tokenKey,
                        tokenRequest.formData(),
                        tokenRequest.basicAuthHeader(),
                        mode))
            .timeout(tokenFetchProperties.overallTimeout())
            .onErrorMap(
                TimeoutException.class,
                error ->
                    new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Timeout occurred while fetching token from " + tokenEndpoint,
                        error))
            .doOnNext(
                tokenInfo -> tokenCache.saveTokenIfFetchMatches(tokenKey, self.get(), tokenInfo))
            .doOnSubscribe(
                ignored -> {
                  log.debug("Creating new token request for key: {}", tokenKey);
                  metrics.fetchStarted();
                })
            .doOnNext(ignored -> metrics.record(Outcome.SUCCESS, mode))
            .doOnError(error -> metrics.record(classifyOutcome(error, mode), mode))
            .doOnTerminate(cleanup)
            .doFinally(signal -> cleanup.run())
            .cache();
    self.set(request);
    return request;
  }

  private Mono<TokenInfo> getAccessTokenQuery(
      String tokenEndpoint,
      String tokenKey,
      MultiValueMap<String, String> formData,
      String basicAuthHeader,
      Mode mode) {

    return oauthTokenUtilWebClient
        .post()
        .uri(tokenEndpoint)
        .headers(
            httpHeaders -> {
              httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
              if (basicAuthHeader != null) {
                httpHeaders.setBasicAuth(basicAuthHeader);
              }
            })
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .onStatus(
            HttpStatusCode::isError, response -> handleIdpError(response, tokenEndpoint, tokenKey))
        .bodyToMono(TokenInfo.class)
        .flatMap(
            tokenInfo ->
                StringUtils.isNotBlank(tokenInfo.getAccessToken())
                    ? Mono.just(applyAccessTokenExpirationFallback(tokenInfo))
                    : Mono.error(
                        new ResponseStatusException(
                            HttpStatus.NOT_ACCEPTABLE,
                            "Identity provider returned an invalid token response from "
                                + tokenEndpoint)))
        .switchIfEmpty(
            Mono.error(
                new ResponseStatusException(
                    HttpStatus.NOT_ACCEPTABLE,
                    "Empty response while fetching token from " + tokenEndpoint)))
        .onErrorMap(
            WebClientResponseException.class,
            ex -> {
              if (ex.getCause() instanceof UnsupportedMediaTypeException cause) {
                return cause;
              }
              return ex;
            })
        .onErrorMap(
            UnsupportedMediaTypeException.class,
            throwable ->
                new ResponseStatusException(
                    HttpStatus.NOT_ACCEPTABLE,
                    "Identity provider returned an invalid token response from " + tokenEndpoint,
                    throwable))
        .doOnError(
            throwable ->
                log.debug(
                    "Token fetch attempt failed: {}: {}",
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage()))
        .retryWhen(
            Retry.backoff(tokenFetchProperties.maxRetries(), tokenFetchProperties.retryBackoff())
                .maxBackoff(tokenFetchProperties.maxRetryBackoff())
                .jitter(0.5)
                .filter(this::isConnectionFailure)
                .doBeforeRetry(ignored -> metrics.recordRetry(mode))
                .onRetryExhaustedThrow(
                    (retryBackoffSpec, retrySignal) ->
                        new TokenFetchUnavailableException(tokenEndpoint, retrySignal.failure())));
  }

  private TokenInfo applyAccessTokenExpirationFallback(TokenInfo tokenInfo) {
    if (tokenInfo.getExpiration() == null) {
      OauthTokenUtil.getExpirationFromAccessToken(tokenInfo.getAccessToken())
          .ifPresent(tokenInfo::setExpiration);
    }
    return tokenInfo;
  }

  Mono<? extends Throwable> handleIdpError(
      ClientResponse response, String tokenEndpoint, String tokenKey) {
    ResponseStatusException error =
        new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Failed to retrieve token from "
                + tokenEndpoint
                + ", original status: "
                + response.statusCode());
    if (!log.isDebugEnabled()) {
      return response
          .releaseBody()
          .onErrorResume(
              bodyError -> {
                log.debug("Failed to release identity provider error response body", bodyError);
                return Mono.empty();
              })
          .thenReturn(error);
    }

    return readErrorBody(response)
        .doOnNext(
            body ->
                log.debug(
                    "Identity provider error while getting token for tokenKey {}: {}",
                    tokenKey,
                    body))
        .thenReturn(error);
  }

  Mono<String> readErrorBody(ClientResponse response) {
    int limit = Math.toIntExact(tokenFetchProperties.errorBodyLogLimit().toBytes());
    ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(limit, 1024));
    AtomicReference<Integer> remaining = new AtomicReference<>(limit);
    return response
        .bodyToFlux(DataBuffer.class)
        .doOnNext(
            dataBuffer -> {
              try {
                int bytesToRead = Math.min(dataBuffer.readableByteCount(), remaining.get());
                if (bytesToRead > 0) {
                  byte[] bytes = new byte[bytesToRead];
                  dataBuffer.read(bytes);
                  body.writeBytes(bytes);
                  remaining.updateAndGet(value -> value - bytesToRead);
                }
              } finally {
                DataBufferUtils.release(dataBuffer);
              }
            })
        .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
        .then()
        .onErrorResume(
            bodyError -> {
              log.debug("Failed to read identity provider error response body", bodyError);
              return Mono.empty();
            })
        .then(Mono.fromSupplier(() -> body.toString(StandardCharsets.UTF_8)));
  }

  private Outcome classifyOutcome(Throwable error, Mode mode) {
    if (error instanceof TokenRequestBuildException) {
      return Outcome.REQUEST_BUILD_FAILURE;
    }
    if (error instanceof TokenFetchUnavailableException) {
      return Outcome.RETRY_EXHAUSTED;
    }
    if (error instanceof ResponseStatusException responseStatusException) {
      return switch (responseStatusException.getStatusCode().value()) {
        case 504 -> Outcome.DEADLINE;
        default -> Outcome.IDP_ERROR;
      };
    }
    return isConnectionFailure(error) ? Outcome.CONNECT_FAILURE : Outcome.IDP_ERROR;
  }

  private boolean isConnectionFailure(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof ConnectException
          || current instanceof ConnectTimeoutException
          || current instanceof NoRouteToHostException
          || current instanceof SslHandshakeTimeoutException
          || current instanceof PrematureCloseException
          || current instanceof UnknownHostException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private record TokenRequest(MultiValueMap<String, String> formData, String basicAuthHeader) {}

  private static final class TokenRequestBuildException extends RuntimeException {

    private TokenRequestBuildException(Throwable cause) {
      super(cause);
    }
  }
}
