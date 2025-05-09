// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration
@ConfigurationProperties(prefix = "jumper.spectre")
@Data
public class SpectreConfiguration {

  private List<MediaType> jsonContentTypes;

  public boolean jsonContentTypesContains(MediaType mediaType) {
    return jsonContentTypes.stream().anyMatch(mediaType::equals);
  }
}
