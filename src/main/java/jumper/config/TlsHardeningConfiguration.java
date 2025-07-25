// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jumper.security.tls")
@Data
public class TlsHardeningConfiguration {

  private List<String> additionalAllowedCipherSuites;
  private List<String> defaultAllowedCipherSuites;
}
