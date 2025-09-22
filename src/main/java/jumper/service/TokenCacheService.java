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

  public String generateTokenCacheKey(String tokenEndpoint, OauthCredentials oauthCredentials) {
    // Use StringBuilder to reduce object allocations
    String scopes = oauthCredentials.getScopes() != null ? oauthCredentials.getScopes() : "";
    return new StringBuilder(
            tokenEndpoint.length() + oauthCredentials.getId().length() + scopes.length() + 2)
        .append(tokenEndpoint)
        .append(TOKEN_CACHE_KEY_DELIMITER)
        .append(oauthCredentials.getId())
        .append(TOKEN_CACHE_KEY_DELIMITER)
        .append(scopes)
        .toString();
  }

  public String generateTokenCacheKey(String tokenEndpoint, String clientID, String scopes) {
    // Use StringBuilder to reduce object allocations - handle null scopes
    String safeScopes = scopes != null ? scopes : "";
    return new StringBuilder(tokenEndpoint.length() + clientID.length() + safeScopes.length() + 2)
        .append(tokenEndpoint)
        .append(TOKEN_CACHE_KEY_DELIMITER)
        .append(clientID)
        .append(TOKEN_CACHE_KEY_DELIMITER)
        .append(safeScopes)
        .toString();
  }

  private boolean isValid(TokenInfo token) {
    return token.getExpiresIn() > this.ttlOffset;
  }
}
