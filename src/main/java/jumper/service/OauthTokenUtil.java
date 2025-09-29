// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static jumper.Constants.TOKEN_REQUEST_METHOD_POST;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.util.BasicAuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;

@Slf4j
@Service
@RequiredArgsConstructor
public class OauthTokenUtil {

  @Qualifier("oauthTokenUtilWebClient")
  private final WebClient oauthTokenUtilWebClient;

  private final TokenCacheService tokenCache;
  private final TokenGeneratorService tokenGenerator;

  private static final JwtParser jwtParser =
      Jwts.parserBuilder().setAllowedClockSkewSeconds(3600).build();

  public static String getTokenWithoutSignature(String consumerToken) {

    if (Objects.isNull(consumerToken)) {
      throw new IllegalStateException("Consumer token not provided, but expected");
    }

    String[] token = consumerToken.split(" ");
    String[] splitToken = token[1].split("\\.");

    return splitToken[0] + "." + splitToken[1] + ".";
  }

  private String getSignature(String consumerToken) {

    if (Objects.isNull(consumerToken)) {
      throw new IllegalStateException("Consumer token not provided, but expected");
    }

    String[] token = consumerToken.split(" ");
    String[] splitToken = token[1].split("\\.");

    return splitToken[2];
  }

  public static String getClaimFromToken(String consumerToken, String claimName) {
    String consumerTokenWithoutSignature = getTokenWithoutSignature(consumerToken);
    return getAllClaimsFromToken(consumerTokenWithoutSignature)
        .getBody()
        .get(claimName, String.class);
  }

  public static Jwt<Header, Claims> getAllClaimsFromToken(String consumerToken) {

    try {
      return jwtParser.parseClaimsJwt(consumerToken);
    } catch (SignatureException e) {
      log.error("SignatureException", e);
    } catch (ExpiredJwtException e) {
      log.error("ExpiredJwtException", e);
    } catch (UnsupportedJwtException e) {
      log.error("UnsupportedJwtException", e);
    } catch (MalformedJwtException e) {
      log.error("MalformedJwtException", e);
    } catch (IllegalArgumentException e) {
      log.error("IllegalArgumentException", e);
    }
    throw new IllegalStateException("Was not able to parse consumer token");
  }

  public String generateEnhancedLastMileGatewayToken(
      JumperConfig jc,
      String operation,
      String issuer,
      String publisherId,
      String subscriberId,
      boolean legacy) {

    String consumerTokenWithoutSignature = getTokenWithoutSignature(jc.getConsumerToken());

    Jwt<Header, Claims> consumerTokenClaims = getAllClaimsFromToken(consumerTokenWithoutSignature);

    Date issuedAt = consumerTokenClaims.getBody().getIssuedAt();
    Date expiration = consumerTokenClaims.getBody().getExpiration();
    String sub = consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_SUB, String.class);
    String aud = consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_AUD, String.class);

    HashMap<String, String> claims = new HashMap<>();
    claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");
    claims.put(Constants.TOKEN_CLAIM_AZP, "stargate");
    claims.put(Constants.TOKEN_CLAIM_SUB, sub);
    claims.put(Constants.TOKEN_CLAIM_REQUEST_PATH, jc.getRequestPath());
    claims.put(Constants.TOKEN_CLAIM_OPERATION, operation);
    claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, jc.getConsumer());
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_ZONE, jc.getConsumerOriginZone());
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, jc.getConsumerOriginStargate());

    if (legacy) {
      String consumerTokenSignature = getSignature(jc.getConsumerToken());
      claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SIGNATURE, consumerTokenSignature);

    } else {
      claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_ENVIRONMENT, jc.getEnvName());

      if (Objects.nonNull(jc.getSecurityScopes())) {
        claims.put(Constants.TOKEN_CLAIM_SCOPE, jc.getSecurityScopes());
      }

      if (Objects.nonNull(publisherId)) {
        claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_PUBLISHER_ID, publisherId);
      }

      if (Objects.nonNull(subscriberId)) {
        claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SUBSCRIBER_ID, subscriberId);
        claims.put(Constants.TOKEN_CLAIM_AUD, subscriberId);
      }
    }

    if (StringUtils.isNotBlank(aud)) {
      claims.put(Constants.TOKEN_CLAIM_AUD, aud);
    }

    return tokenGenerator.fromRealm(claims, issuer, expiration, issuedAt);
  }

  public String generateGatewayTokenForPublisher(String issuer, String realm) {
    HashMap<String, String> claims = new HashMap<>();
    claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");
    claims.put(Constants.TOKEN_CLAIM_AZP, "stargate");
    claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, "gateway");

    return tokenGenerator.fromRealm(
        claims,
        issuer,
        new Date(System.currentTimeMillis() + 300 * 1000),
        new Date(System.currentTimeMillis()));
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

    final String tokenKey = tokenCache.generateTokenCacheKey(tokenEndpoint, clientID, scope);

    // try to get valid token from tokenCache...
    return tokenCache
        .getToken(tokenKey)
        .map(Mono::just)
        .orElseGet(
            () -> { // ...otherwise retrieve a new one
              MultiValueMap<String, String> requestParameter = new LinkedMultiValueMap<>();
              requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ID, clientID);
              requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_CLIENT_SECRET, clientSecret);
              requestParameter.add(
                  Constants.TOKEN_REQUEST_PARAMETER_GRANT_TYPE,
                  AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());

              if (StringUtils.isNotBlank(scope)) {
                requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_SCOPE, scope);
              }

              return getAccessTokenQuery(tokenEndpoint, tokenKey, requestParameter, null);
            });
  }

  public Mono<TokenInfo> getAccessTokenWithOauthCredentialsObject(
      String tokenEndpoint, OauthCredentials oauthCredentials) {

    final String tokenKey = tokenCache.generateTokenCacheKey(tokenEndpoint, oauthCredentials);

    // try to get valid token from tokenCache...
    return tokenCache
        .getToken(tokenKey)
        .map(Mono::just)
        .orElseGet(
            () -> { // ...otherwise retrieve a new one
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
                    && StringUtils.equalsIgnoreCase(
                        TOKEN_REQUEST_METHOD_POST, oauthCredentials.getTokenRequest())) {
                  requestParameter.add(
                      Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ID, oauthCredentials.getClientId());
                  requestParameter.add(
                      Constants.TOKEN_REQUEST_PARAMETER_CLIENT_SECRET,
                      oauthCredentials.getClientSecret());
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
                    Constants.TOKEN_REQUEST_PARAMETER_REFRESH_TOKEN,
                    oauthCredentials.getRefreshToken());
              }

              if (StringUtils.isNotEmpty(oauthCredentials.getScopes())) {
                requestParameter.add(
                    Constants.TOKEN_REQUEST_PARAMETER_SCOPE, oauthCredentials.getScopes());
              }

              requestParameter.add(
                  Constants.TOKEN_REQUEST_PARAMETER_GRANT_TYPE, oauthCredentials.getGrantType());

              return getAccessTokenQuery(tokenEndpoint, tokenKey, requestParameter, basicAuth);
            });
  }

  private String createJwtTokenForExternalIdp(
      String tokenEndpoint, OauthCredentials oauthCredentials) {
    /*
    iss - REQUIRED. Issuer. This MUST contain the client_id of the OAuth Client.
    sub - REQUIRED. Subject. This MUST contain the client_id of the OAuth Client.
    aud - REQUIRED. Audience. The aud (audience) Claim. Value that identifies the Authorization Server as an intended audience. The Authorization Server MUST verify that it is an intended audience for the token. The Audience SHOULD be the URL of the Authorization Server's Token Endpoint.
    jti - REQUIRED. JWT ID. A unique identifier for the token, which can be used to prevent reuse of the token. These tokens MUST only be used once, unless conditions for reuse were negotiated between the parties; any such negotiation is beyond the scope of this specification.
    exp - REQUIRED. Expiration time on or after which the JWT MUST NOT be accepted for processing.
    iat - OPTIONAL. Time at which the JWT was issued.
    */
    HashMap<String, String> claims = new HashMap<>();
    claims.put(Constants.TOKEN_CLAIM_SUB, oauthCredentials.getClientId());
    claims.put(Constants.TOKEN_CLAIM_AUD, tokenEndpoint);
    claims.put(Constants.TOKEN_CLAIM_JTI, UUID.randomUUID().toString());

    return tokenGenerator.fromKey(
        claims,
        oauthCredentials.getClientId(),
        new Date(System.currentTimeMillis() + 60 * 1000),
        new Date(System.currentTimeMillis()),
        oauthCredentials.getClientKey());
  }

  private Mono<TokenInfo> getAccessTokenQuery(
      String tokenEndpoint,
      String tokenKey,
      MultiValueMap<String, String> formData,
      String basicAuthHeader) {

    return oauthTokenUtilWebClient
        .post()
        .uri(tokenEndpoint)
        .headers(
            httpHeaders -> {
              httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
              if (basicAuthHeader != null) httpHeaders.setBasicAuth(basicAuthHeader);
            })
        .body(BodyInserters.fromFormData(formData))
        .retrieve()
        .onStatus(
            HttpStatusCode::is4xxClientError,
            response -> {
              logClientErrorResponse(response, tokenKey);
              return Mono.error(
                  new ResponseStatusException(
                      HttpStatus.UNAUTHORIZED,
                      "Failed to retrieve token from "
                          + tokenEndpoint
                          + ", original status: "
                          + response.statusCode()));
            })
        .onStatus(
            HttpStatusCode::is5xxServerError,
            response -> {
              logClientErrorResponse(response, tokenKey);
              return Mono.error(
                  new ResponseStatusException(
                      HttpStatus.UNAUTHORIZED,
                      "Failed to retrieve token from "
                          + tokenEndpoint
                          + ", original status: "
                          + response.statusCode()));
            })
        .bodyToMono(TokenInfo.class)
        .timeout(Duration.ofSeconds(15))
        .onErrorMap(
            TimeoutException.class,
            throwable ->
                new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Timeout occurred while fetching token from " + tokenEndpoint))
        .onErrorMap(
            UnsupportedMediaTypeException.class,
            throwable ->
                new ResponseStatusException(
                    HttpStatus.NOT_ACCEPTABLE,
                    "Failed while fetching token from "
                        + tokenEndpoint
                        + ": "
                        + throwable.getMessage().replace("bodyType=jumper.model.", "")))
        .doOnError(
            throwable ->
                log.error(
                    "Error occurred class: {}, msg: {}",
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage()))
        .retryWhen(
            Retry.max(2)
                .filter(
                    throwable ->
                        throwable instanceof ConnectTimeoutException
                            || throwable.getCause() instanceof SslHandshakeTimeoutException
                            || throwable.getCause() instanceof PrematureCloseException
                            || throwable.getCause() instanceof UnknownHostException)
                .onRetryExhaustedThrow(
                    (retryBackoffSpec, retrySignal) -> {
                      throw new ResponseStatusException(
                          HttpStatus.UNAUTHORIZED,
                          "Failed to connect to "
                              + tokenEndpoint
                              + ", cause: "
                              + retrySignal.failure().getMessage());
                    }))
        .doOnNext(tokenInfo -> tokenCache.saveToken(tokenKey, tokenInfo));
  }

  private void logClientErrorResponse(ClientResponse response, String tokenKey) {
    response
        .bodyToMono(String.class)
        .publishOn(Schedulers.boundedElastic())
        .subscribe(
            body ->
                log.warn(
                    "Client error occurred while getting token for tokenKey {}: {}",
                    tokenKey,
                    body));
  }
}
