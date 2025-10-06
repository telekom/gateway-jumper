// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class TracingConfigurationTest {

  @Test
  void filterQueryParams() {
    String alreadyEncodedUri =
        "http://localhost:8080/actuator/health?sig=57DjUa%2F9u6KdgCgTZVrHzsm9ZOQA0U%2B3K%2BvqQ7PRrgc%3D";
    String filtered =
        new TracingConfiguration()
            .filterQueryParams(alreadyEncodedUri, List.of(Pattern.compile("nothing")));

    assertEquals(alreadyEncodedUri, filtered);
  }

  @Test
  void filterQueryParamsUnencodedEvenIfUrlIsInvalid() {
    String rawUri =
        "http://localhost:8080/actuator/health?sig=57DjUa/9u6KdgCgTZVrHzsm9ZOQA0U+3K+vqQ7PRrgc=";
    String filtered =
        new TracingConfiguration().filterQueryParams(rawUri, List.of(Pattern.compile("nothing")));

    assertEquals(rawUri, filtered);
  }

  @Test
  void filterBlacklistedQueryParameters() {
    String alreadyEncodedUri =
        "http://localhost:8080/actuator/health?sig-b=57DjUa%2F9u6KdgCgTZVrHzsm9ZOQA0U%2B3K%2BvqQ7PRrgc%3D";
    String filtered =
        new TracingConfiguration()
            .filterQueryParams(alreadyEncodedUri, List.of(Pattern.compile("sig-.*")));

    assertEquals("http://localhost:8080/actuator/health", filtered);
  }
}
