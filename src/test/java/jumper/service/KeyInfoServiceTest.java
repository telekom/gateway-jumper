// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.security.GeneralSecurityException;
import jumper.model.config.KeyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyInfoServiceTest {

  private KeyInfoService testInstance;

  @BeforeEach
  void setUp() {
    testInstance = new KeyInfoService("src/test/resources/keypair", "tls.key", "tls.kid");
  }

  @Test
  void getKeyInfo() throws GeneralSecurityException, IOException {
    // when
    KeyInfo keyInfo = testInstance.getKeyInfo();

    // then
    assertThat(keyInfo).isNotNull();
    assertThat(keyInfo.getKid()).isEqualTo("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    assertThat(keyInfo.getPk()).isNotNull();
  }
}
