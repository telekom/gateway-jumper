// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class BasicAuthUtil {

  public String encodeBasicAuth(String username, String password) {
    Assert.notNull(username, "Username must not be null");
    Assert.doesNotContain(username, ":", "Username must not contain a colon");
    Assert.notNull(password, "Password must not be null");

    String basicAuthPreparation = username + ":" + password;
    return Base64.getEncoder().encodeToString(basicAuthPreparation.getBytes());
  }
}
