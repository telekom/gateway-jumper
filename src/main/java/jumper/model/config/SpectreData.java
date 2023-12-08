// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class SpectreData {
  String consumer; // <consumer-app-id-1>
  String provider; // <provider-app-id-1>
  String issue; // <apiBasePath> | <eventType>
  String kind; // event | request | response
  String method; // GET | POST | PUT | DELETE
  Map<String, String> header = new HashMap<>();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  Map<String, String> parameters = new HashMap<>();

  Object payload;

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  int status;
}
