// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class SleuthConfigurationTest {

  @Test
  void filterQueryParams() {
    String alreadyEncodedUri =
        "http://localhost:8080/actuator/health?sig=57DjUa%2F9u6KdgCgTZVrHzsm9ZOQA0U%2B3K%2BvqQ7PRrgc%3D";
    String filtered = SleuthConfiguration.filterQueryParams(alreadyEncodedUri, List.of("nothing"));

    assertEquals(alreadyEncodedUri, filtered);
  }

  @Test
  void filterQueryParamsUnencodedEvenIfUrlIsInvalid() {
    String rawUri =
        "http://localhost:8080/actuator/health?sig=57DjUa/9u6KdgCgTZVrHzsm9ZOQA0U+3K+vqQ7PRrgc=";
    String filtered = SleuthConfiguration.filterQueryParams(rawUri, List.of("nothing"));

    assertEquals(rawUri, filtered);
  }

  @Test
  void filterBlacklistedQueryParameters() {
    String alreadyEncodedUri =
        "http://localhost:8080/actuator/health?sig-b=57DjUa%2F9u6KdgCgTZVrHzsm9ZOQA0U%2B3K%2BvqQ7PRrgc%3D";
    String filtered = SleuthConfiguration.filterQueryParams(alreadyEncodedUri, List.of("sig-.*"));

    assertEquals("http://localhost:8080/actuator/health", filtered);
  }
}
