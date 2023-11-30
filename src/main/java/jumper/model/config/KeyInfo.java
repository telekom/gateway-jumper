// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KeyInfo {
  private PrivateKey pk;
  private String kid;

  public void setPk(String privateKeyContent)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    KeyFactory kf = KeyFactory.getInstance("RSA");

    PKCS8EncodedKeySpec keySpecPKCS8 =
        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyContent));
    this.pk = kf.generatePrivate(keySpecPKCS8);
  }
}
