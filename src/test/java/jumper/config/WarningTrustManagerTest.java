// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarningTrustManagerTest {

  @Mock private X509TrustManager delegateTrustManager;

  @Mock private X509Certificate mockCertificate;

  private WarningTrustManager warningTrustManager;

  private X509Certificate[] certificateChain;

  @BeforeEach
  void setUp() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    warningTrustManager = new WarningTrustManager(delegateTrustManager, meterRegistry);
    certificateChain = new X509Certificate[] {mockCertificate};
  }

  private void setupCertificateMocks() {
    // Setup certificate mock behavior for tests that need it
    when(mockCertificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test.example.com"));
    when(mockCertificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=Test CA"));
    when(mockCertificate.getNotBefore()).thenReturn(new java.util.Date());
    when(mockCertificate.getNotAfter())
        .thenReturn(new java.util.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
  }

  @Test
  void testCheckServerTrusted_ValidCertificate_NoException() throws CertificateException {
    // Given: certificate mocks and delegate trust manager validates successfully
    setupCertificateMocks();
    doNothing().when(delegateTrustManager).checkServerTrusted(any(), anyString());

    // When: checking server certificate
    // Then: should not throw exception
    assertDoesNotThrow(() -> warningTrustManager.checkServerTrusted(certificateChain, "RSA"));

    // Verify delegate was called
    verify(delegateTrustManager).checkServerTrusted(certificateChain, "RSA");
  }

  @Test
  void testCheckServerTrusted_InvalidCertificate_NoExceptionThrown() throws CertificateException {
    // Given: certificate mocks and delegate trust manager throws exception (invalid certificate)
    setupCertificateMocks();
    doThrow(new CertificateException("Certificate expired"))
        .when(delegateTrustManager)
        .checkServerTrusted(any(), anyString());

    // When: checking server certificate
    // Then: should NOT throw exception (warning mode)
    assertDoesNotThrow(() -> warningTrustManager.checkServerTrusted(certificateChain, "RSA"));

    // Verify delegate was called
    verify(delegateTrustManager).checkServerTrusted(certificateChain, "RSA");
  }

  @Test
  void testCheckServerTrusted_NullChain_NoException() {
    // When: checking null certificate chain
    // Then: should not throw exception, just log warning
    assertDoesNotThrow(() -> warningTrustManager.checkServerTrusted(null, "RSA"));

    // Verify delegate was never called
    verifyNoInteractions(delegateTrustManager);
  }

  @Test
  void testCheckServerTrusted_EmptyChain_NoException() {
    // Given: empty certificate chain
    X509Certificate[] emptyChain = new X509Certificate[0];

    // When: checking empty certificate chain
    // Then: should not throw exception, just log warning
    assertDoesNotThrow(() -> warningTrustManager.checkServerTrusted(emptyChain, "RSA"));

    // Verify delegate was never called
    verifyNoInteractions(delegateTrustManager);
  }

  @Test
  void testCheckClientTrusted_ValidCertificate_NoException() throws CertificateException {
    // Given: certificate mocks and delegate trust manager validates successfully
    setupCertificateMocks();
    doNothing().when(delegateTrustManager).checkServerTrusted(any(), anyString());

    // When: checking client certificate
    // Then: should not throw exception
    assertDoesNotThrow(() -> warningTrustManager.checkClientTrusted(certificateChain, "RSA"));

    // Verify delegate was called
    verify(delegateTrustManager).checkServerTrusted(certificateChain, "RSA");
  }

  @Test
  void testCheckClientTrusted_InvalidCertificate_NoExceptionThrown() throws CertificateException {
    // Given: certificate mocks and delegate trust manager throws exception
    setupCertificateMocks();
    doThrow(new CertificateException("Untrusted issuer"))
        .when(delegateTrustManager)
        .checkServerTrusted(any(), anyString());

    // When: checking client certificate
    // Then: should NOT throw exception (warning mode)
    assertDoesNotThrow(() -> warningTrustManager.checkClientTrusted(certificateChain, "RSA"));

    // Verify delegate was called
    verify(delegateTrustManager).checkServerTrusted(certificateChain, "RSA");
  }

  @Test
  void testGetAcceptedIssuers_DelegatesToWrappedTrustManager() {
    // Given: delegate returns accepted issuers
    X509Certificate[] acceptedIssuers = new X509Certificate[] {mockCertificate};
    when(delegateTrustManager.getAcceptedIssuers()).thenReturn(acceptedIssuers);

    // When: getting accepted issuers
    X509Certificate[] result = warningTrustManager.getAcceptedIssuers();

    // Then: should return delegate's result
    assertSame(acceptedIssuers, result);
    verify(delegateTrustManager).getAcceptedIssuers();
  }
}
