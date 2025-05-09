// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cache-manager")
@Data
public class CacheConfigProperties {

  private List<CaffeineCache> caffeineCaches;

  @Data
  public static class CaffeineCache {
    private List<String> cacheNames;
    private String spec;
  }
}
