// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import jumper.config.RedisConfig;
import jumper.model.config.HealthStatus;
import jumper.model.config.ZoneHealthMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnBean(RedisConfig.class)
@Component
public class RedisZoneHealthStatusService implements MessageListener {

  private final ObjectMapper objectMapper;
  private final ZoneHealthCheckService zoneHealthCheckService;
  private final RedisMessageListenerContainer redisMessageListenerContainer;

  private final String channelKey;

  @Getter private boolean isInitiallySubscribed = false;

  public RedisZoneHealthStatusService(
      ObjectMapper objectMapper,
      ZoneHealthCheckService zoneHealthCheckService,
      RedisMessageListenerContainer redisMessageListenerContainer,
      @Value("${jumper.zone.health.redis.channel}") String channelKey) {
    this.objectMapper = objectMapper;
    this.zoneHealthCheckService = zoneHealthCheckService;
    this.redisMessageListenerContainer = redisMessageListenerContainer;
    this.channelKey = channelKey;

    this.lazyInitializeRedisMessageListenerContainer();
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      ZoneHealthMessage zoneHealthMessage =
          objectMapper.readValue(message.toString(), ZoneHealthMessage.class);
      log.debug("Received message {}", zoneHealthMessage);
      if (zoneHealthMessage.getZone() == null) {
        log.error("Zone is null in message {}, ignoring set status", zoneHealthMessage);
        return;
      }
      zoneHealthCheckService.setZoneHealth(
          zoneHealthMessage.getZone(), zoneHealthMessage.getStatus() == HealthStatus.HEALTHY);
    } catch (JsonProcessingException e) {
      log.error("Error deserializing message", e);
    } catch (Exception e) {
      log.error("Error processing message", e);
    }
  }

  void lazyInitializeRedisMessageListenerContainer() {
    CompletableFuture.supplyAsync(
            () -> {
              var template =
                  new RetryTemplateBuilder()
                      .maxAttempts(Integer.MAX_VALUE)
                      .fixedBackoff(5000)
                      .build();
              return template.execute(
                  context -> {
                    try {
                      if (redisMessageListenerContainer.getConnectionFactory() == null) {
                        log.debug(
                            "Redis connection factory not available, skipping initialization");
                        return false;
                      }

                      var connection =
                          redisMessageListenerContainer.getConnectionFactory().getConnection();
                      if (connection.isSubscribed()) {
                        log.debug("Redis connection already subscribed, skipping initialization");
                        return false;
                      }
                    } catch (Exception e) {
                      log.error(
                          "Connection failure occurred. Restarting subscription task after 5000 ms");
                      throw e;
                    }
                    redisMessageListenerContainer.addMessageListener(
                        this, new ChannelTopic(channelKey));
                    log.info(
                        "Listeners registered successfully after {} retries.",
                        context.getRetryCount());
                    return true;
                  });
            })
        .exceptionally(
            throwable -> {
              log.error(
                  "Stopped initializing Redis message listener container with errors", throwable);
              return false;
            })
        .thenApply(result -> isInitiallySubscribed = result);
  }
}
