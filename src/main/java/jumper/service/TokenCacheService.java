// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jumper.model.TokenInfo;
import jumper.model.config.OauthCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenCacheService {

  @Value("${jumpercache.ttlOffset}")
  private int ttlOffset;

  @Value("${jumpercache.cleanCacheInSeconds:0}")
  private long cleanCacheInSeconds;

  private static final String TOKEN_CACHE_KEY_DELIMITER = ".";

  Map<String, TokenInfo> cachingList = new HashMap<>();

  public TokenCacheService() {

    if (cleanCacheInSeconds > 0) {

      Executors.newScheduledThreadPool(1)
          .scheduleAtFixedRate(
              cleanCacheJob(), cleanCacheInSeconds, cleanCacheInSeconds, TimeUnit.SECONDS);
      log.debug(
          "JumperCache cleanup job is enabled. the cache is cleaned every {} seconds.",
          cleanCacheInSeconds);

    } else {
      log.debug(
          "JumperCache cleanup job is not enabled. Specify a value > 0 in your properties with the key 'jumpercache.cleanCacheInSeconds'.");
    }
  }

  public Optional<TokenInfo> getToken(String tokenCacheKey) {

    log.debug("try to grab token from cache with key: {}", tokenCacheKey);

    if (log.isDebugEnabled()) {
      printCache();
    }

    TokenInfo token = this.cachingList.get(tokenCacheKey);
    if (token != null && !isValid(token)) {

      // delete token and return empty Optional
      log.debug(
          "TTL: {} seconds | The token has expired or will be exipred in less than {} seconds and will be deleted",
          token.getExpiresIn(),
          this.ttlOffset);
      this.deleteTokenByKey(tokenCacheKey);
      return Optional.empty();

    } else {
      return Optional.ofNullable(token);
    }
  }

  public void saveToken(String tokenKey, TokenInfo gwAccessToken) {
    log.debug("Token saved with tokenKey: '{}'", tokenKey);
    this.cachingList.put(tokenKey, gwAccessToken);
  }

  public String generateTokenCacheKey(String tokenEndpoint, OauthCredentials oauthCredentials) {
    return String.join(
        TOKEN_CACHE_KEY_DELIMITER,
        tokenEndpoint,
        oauthCredentials.getId(),
        oauthCredentials.getScopes());
  }

  public String generateTokenCacheKey(String tokenEndpoint, String clientID, String scopes) {
    return String.join(TOKEN_CACHE_KEY_DELIMITER, tokenEndpoint, clientID, scopes);
  }

  public void printCache() {
    log.debug("---Jumper-Cache-List--------------------------------------------");
    log.debug("Number of Tokens in JumperCache: {}", this.cachingList.size());
    this.cachingList.forEach(
        (key, value) -> log.debug("TTL: {} seconds, CacheKey: {}", value.getExpiresIn(), key));
    log.debug("----------------------------------------------------------------");
  }

  private boolean isValid(TokenInfo token) {
    return token.getExpiresIn() > this.ttlOffset;
  }

  private void deleteTokenByKey(String itemKey) {
    this.cachingList.remove(itemKey);
  }

  private Runnable cleanCacheJob() {
    return () -> {
      log.debug(
          "----Clean-Jumper-Cache---Size:{}--------------------------------", cachingList.size());
      cachingList
          .entrySet()
          .removeIf(
              entry -> {
                if (isValid(entry.getValue())) {
                  log.debug(
                      "Valid | TTL={} tokenKey: {}",
                      entry.getValue().getExpiresIn(),
                      entry.getKey());
                  return false;

                } else {
                  log.debug(
                      "Expired -> Delete now | TTL={} tokenKey: {}",
                      entry.getValue().getExpiresIn(),
                      entry.getKey());
                  return true;
                }
              });
      log.debug(
          "----Clean-Jumper-Cache---Size:{}-after-cleaning-------------------------------",
          cachingList.size());
    };
  }
}
