// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ObjectMapperUtil {

  // Jackson 3 ships JDK8 type support (Optional, etc.) in core, so no module needs to be
  // registered. FAIL_ON_UNKNOWN_PROPERTIES already defaults to false in Jackson 3 but is kept
  // explicit to document intent.
  private static final ObjectMapper objectMapper =
      JsonMapper.builder()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .build();

  public static ObjectMapper getInstance() {
    return objectMapper;
  }
}
