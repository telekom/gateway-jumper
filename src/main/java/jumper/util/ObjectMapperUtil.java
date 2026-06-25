// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Static-access bridge to the single, Spring auto-configured Jackson 3 {@link ObjectMapper}. Spring
 * beans should inject the {@code ObjectMapper} directly; this holder exists only for static / non-
 * bean call sites (e.g. {@code JumperConfig} static helpers, {@code JumperInfoResponse#toString})
 * that cannot be autowired. The component is eagerly constructed at context startup, well before
 * any request-handling code calls {@link #getInstance()}.
 */
@Component
public class ObjectMapperUtil {

  private static ObjectMapper objectMapper;

  public ObjectMapperUtil(ObjectMapper objectMapper) {
    ObjectMapperUtil.objectMapper = objectMapper;
  }

  public static ObjectMapper getInstance() {
    return objectMapper;
  }
}
