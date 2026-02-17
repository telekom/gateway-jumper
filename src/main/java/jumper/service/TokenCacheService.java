// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.Optional;
import jumper.model.TokenInfo;
import jumper.model.config.OauthCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenCacheService {

  @Value("${jumper.tokencache.ttlOffset}")
  private int ttlOffset;

  private static final String TOKEN_CACHE_KEY_DELIMITER = ".";
  private static final String TOKEN_CACHE_NAME = "cache-token-info";

  private final Cache tokenCache;

  public TokenCacheService(@Qualifier("caffeineCacheManager") CacheManager cacheManager) {
    this.tokenCache = cacheManager.getCache(TOKEN_CACHE_NAME);
    if (this.tokenCache == null) {
      throw new IllegalStateException(
          "Cache '" + TOKEN_CACHE_NAME + "' not found. Please check cache configuration.");
    }
    log.debug("TokenCacheService initialized with Spring-managed cache: {}", TOKEN_CACHE_NAME);
  }

  public Optional<TokenInfo> getToken(String tokenCacheKey) {
    log.debug("try to grab token from cache with key: {}", tokenCacheKey);

    TokenInfo token = tokenCache.get(tokenCacheKey, TokenInfo.class);

    // Additional TTL validation with offset
    if (token != null && !isValid(token)) {
      log.debug(
          "TTL: {} seconds | Token will expire within {} seconds, removing from cache",
          token.getExpiresIn(),
          this.ttlOffset);
      tokenCache.evict(tokenCacheKey);
      return Optional.empty();
    }

    return Optional.ofNullable(token);
  }

  public void saveToken(String tokenKey, TokenInfo gwAccessToken) {
    log.debug("Token saved with tokenKey: '{}'", tokenKey);
    tokenCache.put(tokenKey, gwAccessToken);
  }

  public void evictToken(String tokenCacheKey) {
    if (tokenCacheKey != null) {
      log.debug("Evicting token from cache with key: '{}'", tokenCacheKey);
      tokenCache.evict(tokenCacheKey);
    }
  }

  public String generateTokenCacheKey(String tokenEndpoint, OauthCredentials oauthCredentials) {
    return generateTokenCacheKey(
        tokenEndpoint,
        oauthCredentials.getId(),
        oauthCredentials.getClientSecret(),
        oauthCredentials.getScopes());
  }

  public String generateTokenCacheKey(
      String tokenEndpoint, String clientID, String clientSecret, String scopes) {
    String safeScopes = scopes != null ? scopes : "";
    String hashedCredentials = hashCredentials(clientID, clientSecret);
    return tokenEndpoint
        + TOKEN_CACHE_KEY_DELIMITER
        + clientID
        + TOKEN_CACHE_KEY_DELIMITER
        + hashedCredentials
        + TOKEN_CACHE_KEY_DELIMITER
        + safeScopes;
  }

  private boolean isValid(TokenInfo token) {
    // If expiration is null (no expires_in in token response), treat as valid.
    // Rely on cache's TTL (30 min max via expireAfterWrite) and 4xx-based eviction.
    if (token.getExpiration() == null) {
      return true;
    }
    return token.getExpiresIn() > this.ttlOffset;
  }

  private String hashCredentials(String clientId, String clientSecret) {
    try {
      String combined =
          (clientId != null ? clientId : "") + (clientSecret != null ? clientSecret : "");
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }
}
