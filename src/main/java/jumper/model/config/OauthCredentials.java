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

    if (this.clientId != null && !this.clientId.isBlank()) {
      return this.clientId;

    } else {
      return this.username;
    }
  }

  /**
   * True if this entry carries any authentication-related configuration — everything except {@code
   * scopes}. Authentication configuration is atomic: an entry carrying any of it is used as-is and
   * never completed from another entry.
   */
  public boolean hasAuthConfig() {
    return StringUtils.isNotBlank(clientId)
        || StringUtils.isNotBlank(clientSecret)
        || StringUtils.isNotBlank(clientKey)
        || StringUtils.isNotBlank(username)
        || StringUtils.isNotBlank(password)
        || StringUtils.isNotBlank(refreshToken)
        || StringUtils.isNotBlank(grantType)
        || StringUtils.isNotBlank(tokenRequest);
  }

  /** Returns a copy of this entry with {@code scopes} replaced. */
  public OauthCredentials withScopes(String newScopes) {
    OauthCredentials copy = new OauthCredentials();
    copy.setClientId(this.clientId);
    copy.setClientSecret(this.clientSecret);
    copy.setClientKey(this.clientKey);
    copy.setScopes(newScopes);
    copy.setUsername(this.username);
    copy.setPassword(this.password);
    copy.setRefreshToken(this.refreshToken);
    copy.setGrantType(this.grantType);
    copy.setTokenRequest(this.tokenRequest);
    return copy;
  }
}
