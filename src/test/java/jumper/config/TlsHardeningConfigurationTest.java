// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TlsHardeningConfigurationTest {

  @Autowired private TlsHardeningConfiguration tlsHardeningConfiguration;

  @Test
  void getAllowedCipherSuites() {
    assertEquals(23, tlsHardeningConfiguration.getDefaultAllowedCipherSuites().size());
    assertEquals(0, tlsHardeningConfiguration.getAdditionalAllowedCipherSuites().size());
  }
}
