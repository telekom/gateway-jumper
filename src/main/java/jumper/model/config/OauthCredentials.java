// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
public class OauthCredentials {
  private String clientId;
  private String clientSecret;
  private String clientKey;
  private String scopes;
  private String username;
  private String password;
  private String refreshToken;
  private String grantType;
  private String tokenRequest;

  public String getId() {
    return StringUtils.isNotBlank(clientId) ? clientId : username;
  }
}
