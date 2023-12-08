// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenInfo {

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("expires_in")
  private int expiresIn;

  private Date expiration;

  @JsonProperty("refresh_expires_in")
  private int refreshExpiresIn;

  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("not-before-policy")
  private int notBeforePolicy;

  @JsonProperty("session_state")
  private String sessionState;

  private String scope;

  public void setExpiresIn(int expiresIn) {
    setExpiration(new Date(System.currentTimeMillis() + expiresIn * 1000L));
  }

  public void setExpiration(Date expiration) {
    this.expiration = expiration;
  }

  public int getExpiresIn() {
    return expiration != null
        ? (int) ((expiration.getTime() - System.currentTimeMillis()) / 1000L)
        : 0;
  }
}
