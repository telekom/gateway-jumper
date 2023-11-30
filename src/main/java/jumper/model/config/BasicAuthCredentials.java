package jumper.model.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BasicAuthCredentials {
  private String username;
  private String password;
}
