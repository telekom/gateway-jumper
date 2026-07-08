// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.zipkin.autoconfigure.ZipkinProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the runtime trace-exporter toggle for {@code TRACING_EXPORTER=zipkin} (the {@code zipkin}
 * profile / {@code application-zipkin.yml}, the default): the Zipkin tracing auto-configuration
 * must be active and the OTLP one excluded. This also pins the SB4 {@code
 * spring.autoconfigure.exclude} class name for OTLP — a stale/typo'd name is silently ignored by
 * Spring Boot, which would leave both exporters active, so the negative assertion is the real
 * guard.
 *
 * <p>The actual {@code SpanExporter} beans are not instantiated under
 * {@code @AutoConfigureTracing}, so the auto-configuration class beans are asserted instead.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "TRACING_URL=http://collector.example:9411/api/v2/spans")
@ActiveProfiles({"test", "zipkin"})
@AutoConfigureTracing
class TracingZipkinExporterProfileTest {

  private static final String OTLP_TRACING_AUTOCONFIG =
      "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp"
          + ".OtlpTracingAutoConfiguration";
  private static final String ZIPKIN_TRACING_AUTOCONFIG =
      "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.zipkin"
          + ".ZipkinWithOpenTelemetryTracingAutoConfiguration";

  @Autowired private ApplicationContext context;

  @Autowired private ZipkinProperties zipkinProperties;

  @Test
  void zipkinTracingAutoConfigurationActiveAndOtlpExcluded() {
    assertThat(context.containsBean(ZIPKIN_TRACING_AUTOCONFIG))
        .withFailMessage(
            "Zipkin tracing auto-configuration must be active under the 'zipkin' profile")
        .isTrue();
    assertThat(context.containsBean(OTLP_TRACING_AUTOCONFIG))
        .withFailMessage(
            "OTLP tracing auto-configuration must be excluded under the 'zipkin' profile")
        .isFalse();
  }

  @Test
  void zipkinEndpointUsesTracingUrl() {
    assertThat(zipkinProperties.getEndpoint())
        .isEqualTo("http://collector.example:9411/api/v2/spans");
  }
}
