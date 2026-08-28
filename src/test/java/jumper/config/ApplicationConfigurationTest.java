// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApplicationConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer());

  @Test
  void managementPortIsUnsetByDefault() {
    contextRunner.run(
        context -> {
          var environment = context.getEnvironment();

          assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8080);
          assertThat(environment.getProperty("management.server.port", Integer.class)).isNull();
          assertThat(ManagementPortType.get(environment)).isEqualTo(ManagementPortType.SAME);
          assertThat(
                  environment.getProperty(
                      "management.endpoint.health.probes.add-additional-paths", Boolean.class))
              .isTrue();
        });
  }

  @Test
  void managementPortStaysUnsetWhenApplicationPortIsConfigured() {
    contextRunner
        .withPropertyValues("JUMPER_PORT=8181")
        .run(
            context -> {
              var environment = context.getEnvironment();

              assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8181);
              assertThat(environment.getProperty("management.server.port", Integer.class)).isNull();
              assertThat(ManagementPortType.get(environment)).isEqualTo(ManagementPortType.SAME);
            });
  }

  @Test
  void managementSharesApplicationListenerWhenServerPortIsOverridden() {
    contextRunner
        .withPropertyValues("server.port=9000")
        .run(
            context -> {
              var environment = context.getEnvironment();

              assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(9000);
              assertThat(environment.getProperty("management.server.port", Integer.class)).isNull();
              assertThat(ManagementPortType.get(environment)).isEqualTo(ManagementPortType.SAME);
            });
  }

  @Test
  void managementPortCanBeConfiguredSeparately() {
    contextRunner
        .withPropertyValues("JUMPER_PORT=8181", "JUMPER_MANAGEMENT_PORT=9090")
        .run(
            context -> {
              var environment = context.getEnvironment();

              assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8181);
              assertThat(environment.getProperty("management.server.port", Integer.class))
                  .isEqualTo(9090);
            });
  }

  @Test
  void managementPortCanBeConfiguredWhenApplicationPortIsAbsent() {
    contextRunner
        .withPropertyValues("JUMPER_MANAGEMENT_PORT=9090")
        .run(
            context -> {
              var environment = context.getEnvironment();

              assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8080);
              assertThat(environment.getProperty("management.server.port", Integer.class))
                  .isEqualTo(9090);
            });
  }
}
