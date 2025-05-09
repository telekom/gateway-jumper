// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Utility class for handling RSA private keys. */
public class RsaUtils {

  private static final String PEM_PREFIX = "-----BEGIN PRIVATE KEY-----";
  private static final String PEM_SUFFIX = "-----END PRIVATE KEY-----";

  public static PrivateKey getPrivateKey(Path filePath)
      throws GeneralSecurityException, IOException {
    String privateKeyPEM = Files.readString(filePath);
    return getPrivateKey(privateKeyPEM);
  }

  public static PrivateKey getPrivateKey(String key) throws GeneralSecurityException {
    String privateKeyPEM = removePemHeaders(key);

    KeyFactory kf = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpecPKCS8 =
        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyPEM));
    return kf.generatePrivate(keySpecPKCS8);
  }

  private static String removePemHeaders(String pem) {
    return pem.replace(PEM_PREFIX, "").replace(PEM_SUFFIX, "").replaceAll("\\s", "");
  }
}
