// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import jumper.config.OauthTokenFetchProperties;
import jumper.model.TokenInfo;
import jumper.model.config.OauthCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class TokenCacheService {

  private static final String TOKEN_CACHE_KEY_DELIMITER = ".";
  private static final String TOKEN_CACHE_NAME = "cache-token-info";

  private final Cache tokenCache;
  private final OauthTokenFetchProperties tokenFetchProperties;
  private final Clock clock;
  private final ConcurrentHashMap<String, Mono<TokenInfo>> activeFetches =
      new ConcurrentHashMap<>();

  @Autowired
  public TokenCacheService(
      @Qualifier("caffeineCacheManager") CacheManager cacheManager,
      OauthTokenFetchProperties tokenFetchProperties) {
    this(cacheManager, tokenFetchProperties, Clock.systemUTC());
  }

  TokenCacheService(
      CacheManager cacheManager, OauthTokenFetchProperties tokenFetchProperties, Clock clock) {
    this.tokenCache = cacheManager.getCache(TOKEN_CACHE_NAME);
    this.tokenFetchProperties = tokenFetchProperties;
    this.clock = clock;
    if (this.tokenCache == null) {
      throw new IllegalStateException(
          "Cache '" + TOKEN_CACHE_NAME + "' not found. Please check cache configuration.");
    }
    log.debug("TokenCacheService initialized with Spring-managed cache: {}", TOKEN_CACHE_NAME);
  }

  public TokenLookup lookup(String tokenCacheKey) {
    log.debug("Looking up token from cache with key: {}", tokenCacheKey);

    TokenInfo token = tokenCache.get(tokenCacheKey, TokenInfo.class);
    if (token == null) {
      return new TokenLookup(null, Freshness.NOT_SERVABLE);
    }
    if (token.getExpiration() == null) {
      return new TokenLookup(token, Freshness.FRESH);
    }

    Duration remaining = Duration.ofMillis(token.getExpiration().getTime() - clock.millis());
    if (remaining.compareTo(tokenFetchProperties.minServe()) <= 0) {
      return new TokenLookup(null, Freshness.NOT_SERVABLE);
    }

    Freshness freshness =
        remaining.compareTo(tokenFetchProperties.refreshAhead()) <= 0
            ? Freshness.NEEDS_REFRESH
            : Freshness.FRESH;
    return new TokenLookup(token, freshness);
  }

  void saveToken(String tokenKey, TokenInfo gwAccessToken) {
    log.debug("Token saved with tokenKey: '{}'", tokenKey);
    tokenCache.put(tokenKey, gwAccessToken);
  }

  /**
   * Atomically selects the active fetch for a token key. The caller owns subscribing to the
   * candidate only when {@link FetchSelection#created()} is true.
   */
  public FetchSelection getOrCreateFetch(String tokenKey, Mono<TokenInfo> candidate) {
    boolean[] created = {false};
    Mono<TokenInfo> selected =
        activeFetches.compute(
            tokenKey,
            (key, existing) -> {
              if (existing != null) {
                return existing;
              }
              created[0] = true;
              return candidate;
            });
    return new FetchSelection(selected, created[0]);
  }

  /**
   * Saves a fetched token only while that exact fetch is still current for the key. Cache writes
   * intentionally run inside the per-key map operation and must not re-enter {@code activeFetches};
   * this linearizes the identity check and write against eviction.
   */
  public boolean saveTokenIfFetchMatches(String tokenKey, Mono<TokenInfo> fetch, TokenInfo token) {
    boolean[] saved = new boolean[1];
    activeFetches.computeIfPresent(
        tokenKey,
        (key, current) -> {
          if (current == fetch) {
            tokenCache.put(tokenKey, token);
            saved[0] = true;
            log.debug("Token saved with tokenKey: '{}'", tokenKey);
          }
          return current;
        });
    return saved[0];
  }

  /** Removes a completed fetch without removing a replacement installed after eviction. */
  public void completeFetch(String tokenKey, Mono<TokenInfo> fetch) {
    activeFetches.remove(tokenKey, fetch);
  }

  /**
   * Atomically invalidates any active fetch and evicts its cached token. Cache eviction
   * intentionally runs inside the per-key map operation and must not re-enter {@code
   * activeFetches}.
   */
  public void evictToken(String tokenCacheKey) {
    if (tokenCacheKey != null) {
      // Keep invalidation and eviction in one per-key critical section. Otherwise an older fetch
      // could save between those operations and repopulate a token rejected by the provider.
      activeFetches.compute(
          tokenCacheKey,
          (key, fetch) -> {
            log.debug("Evicting token from cache with key: '{}'", tokenCacheKey);
            tokenCache.evict(tokenCacheKey);
            return null;
          });
    }
  }

  int activeFetchCount() {
    return activeFetches.size();
  }

  public String generateTokenCacheKey(String tokenEndpoint, OauthCredentials oauthCredentials) {
    return generateTokenCacheKey(
        tokenEndpoint,
        oauthCredentials.getClientId(),
        oauthCredentials.getClientSecret(),
        oauthCredentials.getClientKey(),
        oauthCredentials.getUsername(),
        oauthCredentials.getPassword(),
        oauthCredentials.getRefreshToken(),
        oauthCredentials.getScopes(),
        oauthCredentials.getGrantType());
  }

  public String generateTokenCacheKey(
      String tokenEndpoint, String clientID, String clientSecret, String scopes) {
    return generateTokenCacheKey(
        tokenEndpoint,
        clientID,
        clientSecret,
        null,
        null,
        null,
        null,
        scopes,
        AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());
  }

  private String generateTokenCacheKey(String tokenEndpoint, String... credentialFields) {
    return tokenEndpoint + TOKEN_CACHE_KEY_DELIMITER + hashCredentials(credentialFields);
  }

  private String hashCredentials(String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String value : values) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
      }
      byte[] hash = digest.digest();
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 algorithm not available", error);
    }
  }

  public enum Freshness {
    FRESH,
    NEEDS_REFRESH,
    NOT_SERVABLE
  }

  public record TokenLookup(TokenInfo token, Freshness freshness) {

    public TokenLookup {
      Objects.requireNonNull(freshness, "freshness");
      if (freshness != Freshness.NOT_SERVABLE) {
        Objects.requireNonNull(token, "A servable token lookup requires a token");
      }
    }

    public boolean servable() {
      return freshness != Freshness.NOT_SERVABLE;
    }

    public boolean needsRefresh() {
      return freshness == Freshness.NEEDS_REFRESH;
    }
  }

  public record FetchSelection(Mono<TokenInfo> publisher, boolean created) {}
}
