// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.Base64;
import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.util.HeaderUtil;
import jumper.util.ObjectMapperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

@Slf4j
@Component
public class JumperConfigHeaderReader {

  public JumperConfig readJumperConfig(ServerHttpRequest request) {
    return fromJsonBase64(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));
  }

  public List<JumperConfig> readRoutingConfigs(ServerHttpRequest request) {
    String routingConfigBase64 =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ROUTING_CONFIG);

    if (StringUtils.isBlank(routingConfigBase64)) {
      throw new RuntimeException("can not base64decode header: " + routingConfigBase64);
    }

    return fromJsonBase64(routingConfigBase64, new TypeReference<>() {});
  }

  public static String toJsonBase64(Object value) {
    String jsonConfigBase64 = null;
    try {
      String decodedJson = ObjectMapperUtil.getInstance().writeValueAsString(value);
      jsonConfigBase64 = Base64.getEncoder().encodeToString(decodedJson.getBytes());
    } catch (JacksonException e) {
      log.error("can not base64encode object: " + value);
    }

    return jsonConfigBase64;
  }

  public static JumperConfig fromJsonBase64(String jsonConfigBase64) {
    if (StringUtils.isNotBlank(jsonConfigBase64)) {
      return fromJsonBase64(jsonConfigBase64, new TypeReference<>() {});
    }

    return new JumperConfig();
  }

  private static <T> T fromJsonBase64(String jsonConfigBase64, TypeReference<T> typeReference) {
    String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));
    try {
      return ObjectMapperUtil.getInstance().readValue(decodedJson, typeReference);
    } catch (JacksonException e) {
      throw new RuntimeException("can not base64decode header: " + jsonConfigBase64);
    }
  }
}
