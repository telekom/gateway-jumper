// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CachingConfig {

  @Bean
  @Qualifier("cacheKeyInfo")
  public Cache<Object, Object> cacheKeyInfo(
      @Value("${jumper.cache.key-info.max-size:1}") int maxSize,
      @Value("${jumper.cache.key-info.expire-after-write-minutes:1}") int expireAfterWriteMinutes) {
    return Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
        .recordStats()
        .build();
  }

  @Bean
  @Qualifier("cacheTokenInfo")
  public Cache<Object, Object> cacheTokenInfo(
      @Value("${jumper.cache.token-info.max-size:10000}") int maxSize,
      @Value("${jumper.cache.token-info.expire-after-write-minutes:30}")
          int expireAfterWriteMinutes) {
    return Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
        .recordStats()
        .build();
  }

  @Bean
  @Qualifier("caffeineCacheManager")
  public CacheManager caffeineCacheManager(
      @Qualifier("cacheKeyInfo") Cache<Object, Object> cacheKeyInfo,
      @Qualifier("cacheTokenInfo") Cache<Object, Object> cacheTokenInfo) {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setAllowNullValues(false);
    manager.registerCustomCache("cache-key-info", cacheKeyInfo);
    manager.registerCustomCache("cache-token-info", cacheTokenInfo);
    return manager;
  }
}
