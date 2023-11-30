package jumper.model.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OauthCredentials {
  private String clientId;
  private String clientSecret;
  private String scopes;
  private String username;
  private String password;
  private String refreshToken;
  private String grantType;

  public String getId() {

    if (this.clientId != null && !this.clientId.isBlank()) {
      return this.clientId;

    } else {
      return this.username;
    }
  }
}
