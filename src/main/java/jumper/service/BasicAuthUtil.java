package jumper.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;

@Service
@RequiredArgsConstructor
public class BasicAuthUtil {

  public String encodeBasicAuth(String username, String password) {
    Assert.notNull(username, "Username must not be null");
    Assert.doesNotContain(username, ":", "Username must not contain a colon");
    Assert.notNull(password, "Password must not be null");

    String basicAuthPreparation = username + ":" + password;
    return Base64Utils.encodeToString(basicAuthPreparation.getBytes());
  }
}
