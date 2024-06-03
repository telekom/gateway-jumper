// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import jumper.model.config.HealthStatus;
import jumper.model.config.ZoneHealthMessage;
import jumper.util.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RedisZoneHealthStatusServiceTest extends AbstractIntegrationTest {

  @Value("${jumper.zone.health.redis.channel}")
  private String channelKey;

  @Autowired private RedisTemplate<String, String> redisTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RedisZoneHealthStatusService redisZoneHealthStatusService;

  @SpyBean private ZoneHealthCheckService zoneHealthCheckService;

  @BeforeEach
  void setUp() {
    Mockito.reset(zoneHealthCheckService);
  }

  @Test
  @DisplayName(
      "Test if a zone is marked correctly after receiving message via redis with a unhealthy status message")
  void getZoneUnhealthyWithRedisPubSubListener() throws JsonProcessingException {
    // given
    String zoneToTest = "zoneToTest";
    ZoneHealthMessage message = new ZoneHealthMessage(zoneToTest, HealthStatus.UNHEALTHY);
    var messageString = objectMapper.writeValueAsString(message);
    await()
        .atMost(Duration.ofSeconds(15))
        .until(() -> redisZoneHealthStatusService.isInitiallySubscribed());

    // when
    redisTemplate.convertAndSend(channelKey, messageString);

    // then
    Mockito.verify(zoneHealthCheckService, Mockito.timeout(5000L).times(1))
        .setZoneHealth(Mockito.eq(zoneToTest), Mockito.eq(false));
    assertFalse(zoneHealthCheckService.getZoneHealth(zoneToTest));
  }

  @ParameterizedTest
  @DisplayName(
      "Test if a zone is marked correctly healthy after receiving a incompatible message via redis with a unhealthy status message")
  @ValueSource(
      strings = {
        """
			{
			"zone": "%s",
			"status": "UNHEALTHYHELLO"
			}
	""",
        """
			{
			"status": "UNHEALTHY"
			}
			"""
      })
  void getZoneHealthyWithRedisPubSubListenerForMalformedMessage(String messageTemplate) {
    // given
    String zoneToTest = "wrongFormatZone";
    var messageString = String.format(messageTemplate, zoneToTest);

    // when
    redisZoneHealthStatusService.onMessage(
        new DefaultMessage(channelKey.getBytes(), messageString.getBytes()), null);

    // then
    Mockito.verify(zoneHealthCheckService, Mockito.times(0))
        .setZoneHealth(Mockito.anyString(), Mockito.anyBoolean());
    Mockito.verify(zoneHealthCheckService, Mockito.times(0))
        .setZoneHealth(Mockito.isNull(), Mockito.anyBoolean());
    assertTrue(zoneHealthCheckService.getZoneHealth(zoneToTest));
  }
}
