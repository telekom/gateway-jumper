// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@ToString
public class OauthCredentials {
  private String clientId;

  @ToString.Exclude private String clientSecret;

  @ToString.Exclude private String clientKey;
  private String scopes;
  private String username;

  @ToString.Exclude private String password;

  @ToString.Exclude private String refreshToken;
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

  /**
   * Reports whether these credentials carry at least one authentication mechanism that {@code
   * TokenFetchService#getAccessTokenWithOauthCredentialsObject} can actually put on the wire:
   * client authentication via client secret or client key (JWT client assertion), resource owner
   * credentials, or a refresh token. If none of them applies, the token request body would consist
   * of nothing but {@code scope} and {@code grant_type}.
   *
   * <p>Resource owner credentials and a refresh token are accepted without any client
   * authentication because public clients are an established configuration here. Such a request can
   * still be rejected by the identity provider - this predicate only rules out the requests that
   * cannot possibly succeed.
   *
   * <p>Deliberately not the same predicate as {@link #hasAnyCredentialField()}: that one asks
   * whether an entry claims an identity at all (to decide whether it may be completed from the
   * provider default), this one asks whether a complete request can be built from it.
   */
  public boolean canBuildTokenRequest() {
    // clientId is required for both secret and key: the JWT client assertion uses it as iss, sub
    // and client_id, so a key without an id cannot identify the client either.
    boolean hasClientAuth =
        StringUtils.isNotBlank(clientId)
            && (StringUtils.isNotBlank(clientSecret) || StringUtils.isNotBlank(clientKey));

    return hasClientAuth
        || (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password))
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
