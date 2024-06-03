// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import io.lettuce.core.metrics.MicrometerOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "jumper.zone.health.enabled", havingValue = "true")
public class RedisConfig {

  // in newer versions this should not prevent app startup on missing redis connection
  // https://stackoverflow.com/questions/72436006/spring-boot-2-7-redis-pub-sub-fails-startup-on-missing-redis-connection
  @Bean
  public RedisMessageListenerContainer redisContainer(
      LettuceConnectionFactory lettuceConnectionFactory) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(lettuceConnectionFactory);
    // the following causes the app to fail on startup if redis is not available
    // container.addMessageListener(statusService, new ChannelTopic(statusService.getChannelKey()));

    return container;
  }

  @Bean
  MicrometerOptions micrometerOptions() {
    return MicrometerOptions.builder().histogram(false).build();
  }
}
