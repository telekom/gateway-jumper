// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JsonConverter {

  private final ObjectMapper objectMapper;

  public String toJsonBase64(Object o) {
    String jsonConfigBase64 = null;
    try {
      String decodedJson = objectMapper.writeValueAsString(o);
      jsonConfigBase64 = Base64.getEncoder().encodeToString(decodedJson.getBytes());
    } catch (JsonProcessingException e) {
      log.error("can not base64encode object: {}", o);
    }

    return jsonConfigBase64;
  }

  public String toJson(Object o) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      log.error("can not convert object to json-string: {}", o);
    }

    return "";
  }

  public <T> T fromJsonBase64(String jsonConfigBase64, TypeReference<T> typeReference) {
    String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));
    try {
      return objectMapper.readValue(decodedJson, typeReference);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("can not base64decode header: " + jsonConfigBase64);
    }
  }
}
