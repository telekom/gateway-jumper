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
   * Returns a new instance with the values of this (consumer) entry; every blank field is inherited
   * from {@code base} (the "default" provider entry). A non-blank consumer field always wins —
   * including {@code scopes}, which is replaced as a whole, never combined with the default scopes.
   */
  public OauthCredentials withDefaults(OauthCredentials base) {
    if (base == null) {
      return this;
    }
    OauthCredentials merged = new OauthCredentials();
    merged.setClientId(firstNonBlank(this.clientId, base.clientId));
    merged.setClientSecret(firstNonBlank(this.clientSecret, base.clientSecret));
    merged.setClientKey(firstNonBlank(this.clientKey, base.clientKey));
    merged.setScopes(firstNonBlank(this.scopes, base.scopes));
    merged.setUsername(firstNonBlank(this.username, base.username));
    merged.setPassword(firstNonBlank(this.password, base.password));
    merged.setRefreshToken(firstNonBlank(this.refreshToken, base.refreshToken));
    merged.setGrantType(firstNonBlank(this.grantType, base.grantType));
    merged.setTokenRequest(firstNonBlank(this.tokenRequest, base.tokenRequest));
    return merged;
  }

  private static String firstNonBlank(String preferred, String fallback) {
    return StringUtils.isNotBlank(preferred) ? preferred : fallback;
  }
}
