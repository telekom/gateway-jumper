// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TlsHardeningConfigurationTest {

  @Autowired private TlsHardeningConfiguration tlsHardeningConfiguration;

  @Test
  void getAllowedCipherSuites() {
    assertEquals(22, tlsHardeningConfiguration.getDefaultAllowedCipherSuites().size());
    assertEquals(0, tlsHardeningConfiguration.getAdditionalAllowedCipherSuites().size());
  }

  @Test
  void testConfiguredCipherSuitesAreSupportedByJvm() throws Exception {
    // Get all configured cipher suites (default + additional)
    List<String> allConfiguredCipherSuites =
        Stream.concat(
                tlsHardeningConfiguration.getDefaultAllowedCipherSuites().stream(),
                tlsHardeningConfiguration.getAdditionalAllowedCipherSuites().stream())
            .distinct()
            .toList();

    // Create SSL engine to get JVM supported cipher suites
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, null, null);
    SSLEngine sslEngine = sslContext.createSSLEngine();

    // Get all cipher suites supported by the JVM
    Set<String> jvmSupportedCipherSuites =
        Arrays.stream(sslEngine.getSupportedCipherSuites()).collect(Collectors.toSet());

    // Verify each configured cipher suite is supported by the JVM
    List<String> unsupportedCipherSuites =
        allConfiguredCipherSuites.stream()
            .filter(cipherSuite -> !jvmSupportedCipherSuites.contains(cipherSuite))
            .toList();

    // Assert that all configured cipher suites are supported
    assertTrue(
        unsupportedCipherSuites.isEmpty(),
        "The following configured cipher suites are not supported by the JVM: "
            + unsupportedCipherSuites);

    // Additional assertion to ensure we actually tested something
    assertFalse(allConfiguredCipherSuites.isEmpty(), "No cipher suites configured to test");
  }
}
