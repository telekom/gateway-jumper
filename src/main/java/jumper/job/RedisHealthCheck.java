// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.job;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import jumper.config.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnBean(RedisConfig.class)
@Component
@EnableScheduling
public class RedisHealthCheck {

  private static final String CONNECT = "CONNECTED";

  private final RedisConnectionFactory redisConnectionFactory;
  private final AtomicInteger redisConnectionStatus;

  public RedisHealthCheck(RedisConnectionFactory connectionFactory, MeterRegistry meterRegistry) {
    Assert.notNull(connectionFactory, "ConnectionFactory must not be null");
    this.redisConnectionFactory = connectionFactory;

    this.redisConnectionStatus = new AtomicInteger(0);
    Gauge.builder("zone.health.redis.connection", () -> redisConnectionStatus)
        .description("redis connection status")
        .tag("status", CONNECT)
        .register(meterRegistry);
  }

  @Scheduled(fixedRateString = "${jumper.zone.health.redis.checkConnectionInterval}")
  public void refreshZoneHealthStatus() {
    try {
      this.doHealthCheck();
      gaugeEvaluation(true);
    } catch (Exception e) {
      log.error("Error while checking Redis health", e);
      gaugeEvaluation(false);
    }
  }

  private void doHealthCheck() {
    RedisConnection connection = RedisConnectionUtils.getConnection(this.redisConnectionFactory);
    try {
      this.doHealthCheck(connection);
    } finally {
      RedisConnectionUtils.releaseConnection(connection, this.redisConnectionFactory);
    }
  }

  private void doHealthCheck(RedisConnection connection) {
    if (connection instanceof RedisClusterConnection) {
      ((RedisClusterConnection) connection).clusterGetClusterInfo();
    } else {
      connection.info("server");
    }
  }

  private void gaugeEvaluation(Boolean isConnected) {
    redisConnectionStatus.set(isConnected ? 1 : 0);
  }
}
