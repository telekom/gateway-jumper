// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the runtime trace-exporter toggle for {@code TRACING_EXPORTER=otlp} (the {@code otlp}
 * profile / {@code application-otlp.yml}): the OTLP tracing auto-configuration must be active and
 * the Zipkin one excluded. This also pins the SB4 {@code spring.autoconfigure.exclude} class name
 * for Zipkin — a stale/typo'd name is silently ignored by Spring Boot, which would leave both
 * exporters active, so the negative assertion is the real guard.
 *
 * <p>The actual {@code SpanExporter} beans are not instantiated under
 * {@code @AutoConfigureTracing}, so the auto-configuration class beans are asserted instead.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "TRACING_URL=http://collector.example:4318/v1/traces")
@ActiveProfiles({"test", "otlp"})
@AutoConfigureTracing
class TracingOtlpExporterProfileTest {

  private static final String OTLP_TRACING_AUTOCONFIG =
      "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp"
          + ".OtlpTracingAutoConfiguration";
  private static final String ZIPKIN_TRACING_AUTOCONFIG =
      "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.zipkin"
          + ".ZipkinWithOpenTelemetryTracingAutoConfiguration";
  private static final String ZIPKIN_AUTOCONFIG =
      "org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration";

  @Autowired private ApplicationContext context;

  @Autowired private OtlpTracingProperties otlpTracingProperties;

  @Test
  void otlpTracingAutoConfigurationActiveAndZipkinExcluded() {
    assertThat(context.containsBean(OTLP_TRACING_AUTOCONFIG))
        .withFailMessage("OTLP tracing auto-configuration must be active under the 'otlp' profile")
        .isTrue();
    assertThat(context.containsBean(ZIPKIN_TRACING_AUTOCONFIG))
        .withFailMessage(
            "Zipkin tracing auto-configuration must be excluded under the 'otlp' profile")
        .isFalse();
    assertThat(context.containsBean(ZIPKIN_AUTOCONFIG))
        .withFailMessage(
            "Zipkin sender auto-configuration must be excluded under the 'otlp' profile")
        .isFalse();
  }

  @Test
  void otlpEndpointUsesTracingUrl() {
    assertThat(otlpTracingProperties.getEndpoint())
        .isEqualTo("http://collector.example:4318/v1/traces");
  }
}
