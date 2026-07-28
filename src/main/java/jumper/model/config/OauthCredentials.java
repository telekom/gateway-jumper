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
   * Indicates whether this entry carries any field that identifies or authenticates a client
   * against the identity provider. Scopes, grantType and tokenRequest are deliberately excluded:
   * they describe how a token is requested, not with which identity.
   */
  public boolean hasAnyCredentialField() {
    return StringUtils.isNotBlank(clientId)
        || StringUtils.isNotBlank(clientSecret)
        || StringUtils.isNotBlank(clientKey)
        || StringUtils.isNotBlank(username)
        || StringUtils.isNotBlank(password)
        || StringUtils.isNotBlank(refreshToken);
  }

  /** Returns a copy of this entry with the scopes replaced. The receiver is left untouched. */
  public OauthCredentials copyWithScopes(String scopes) {
    OauthCredentials copy = new OauthCredentials();
    copy.setClientId(clientId);
    copy.setClientSecret(clientSecret);
    copy.setClientKey(clientKey);
    copy.setScopes(scopes);
    copy.setUsername(username);
    copy.setPassword(password);
    copy.setRefreshToken(refreshToken);
    copy.setGrantType(grantType);
    copy.setTokenRequest(tokenRequest);
    return copy;
  }
}
