// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jumper.warmup")
@Data
public class WarmupProperties {

  private boolean enabled = false;
  private Duration timeout = Duration.ofSeconds(15);
  private List<String> urls = List.of();
}
