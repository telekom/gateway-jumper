// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import jumper.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class NettyMetricsConfig {

  @Bean
  public NettyServerCustomizer nettyServerCustomizer() {
    return httpServer -> {
      log.info("NettyServerCustomizer applied");
      return httpServer.metrics(true, uri -> Constants.PROXY_ROOT_PATH_PREFIX);
    };
  }
}
