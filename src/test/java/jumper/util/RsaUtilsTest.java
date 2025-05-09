// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RsaUtilsTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("pathProvider")
  void getPrivateKeyFromPath(String displayName, Path path, Exception e) {
    try {
      RsaUtils.getPrivateKey(path);
      if (e != null) {
        throw new AssertionError("Expected exception: " + e.getClass().getName());
      }
    } catch (Exception ex) {
      if (e == null) {
        throw new AssertionError("Unexpected exception: " + ex.getClass().getName());
      }
      assertThat(ex).isInstanceOf(e.getClass());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stringProvider")
  void getPrivateKeyFromString(String displayName, String pemString, Exception e) {
    try {
      RsaUtils.getPrivateKey(pemString);
      if (e != null) {
        throw new AssertionError("Expected exception: " + e.getClass().getName());
      }
    } catch (Exception ex) {
      if (e == null) {
        throw new AssertionError("Unexpected exception: " + ex.getClass().getName());
      }
      assertThat(ex).isInstanceOf(e.getClass());
    }
  }

  private static Stream<Arguments> pathProvider() {
    return Stream.of(
        Arguments.of(
            "Valid RSA private key path",
            new File("src/test/resources/keypair/tls.key").toPath(),
            null),
        Arguments.of(
            "Not valid RSA private key path (secp256r1)",
            new File("src/test/resources/keypair/invalid_tls.key").toPath(),
            new IllegalArgumentException()),
        Arguments.of(
            "Non-existing private key path",
            new File("src/test/resources/keypair/non_existing.key").toPath(),
            new IOException()));
  }

  private static Stream<Arguments> stringProvider() throws IOException {
    return Stream.of(
        Arguments.of(
            "Valid RSA private key pem-string",
            Files.readString(new File("src/test/resources/keypair/tls.key").toPath()),
            null),
        Arguments.of(
            "Not valid RSA private key pem-string (secp256r1)",
            Files.readString(new File("src/test/resources/keypair/invalid_tls.key").toPath()),
            new IllegalArgumentException()));
  }
}
