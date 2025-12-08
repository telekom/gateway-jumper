// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;

/**
 * A TrustManager that validates certificates using a delegate TrustManager but logs warnings
 * instead of failing the connection when validation fails.
 *
 * <p>This allows for observability of certificate issues in production without breaking existing
 * connections to services with invalid certificates.
 *
 * <p>Records Prometheus metrics for certificate validation failures including hostname and failure
 * reason.
 */
@Slf4j
public class WarningTrustManager extends X509ExtendedTrustManager {

  private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");
  private static final String METRIC_CERT_VALIDATION = "jumper.ssl.certificate.validation";

  private final X509TrustManager delegateTrustManager;
  private final MeterRegistry meterRegistry;

  public WarningTrustManager(X509TrustManager delegateTrustManager, MeterRegistry meterRegistry) {
    this.delegateTrustManager = delegateTrustManager;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    checkServerTrusted(chain, authType);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    checkServerTrusted(chain, authType);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    validateWithWarning(chain, authType, "client");
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    validateWithWarning(chain, authType, "server");
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return delegateTrustManager.getAcceptedIssuers();
  }

  private void validateWithWarning(X509Certificate[] chain, String authType, String type) {
    if (chain == null || chain.length == 0) {
      log.warn(
          "Certificate validation warning: No certificate chain provided for {} authentication",
          type);
      recordValidationFailure("unknown", "unknown", "no_certificate_chain", type);
      return;
    }

    X509Certificate cert = chain[0];
    String hostname = extractHostname(cert);
    String issuer = extractCommonName(cert.getIssuerX500Principal().getName());

    try {
      // Attempt validation with the delegate trust manager
      delegateTrustManager.checkServerTrusted(chain, authType);
      log.debug("Certificate validation successful for {}", getCertificateInfo(cert));
      recordValidationSuccess(hostname, issuer, type);
    } catch (CertificateException e) {
      String reason = categorizeFailureReason(e);

      // Log warning but don't throw - allow connection to proceed
      log.warn(
          "Certificate validation failed for {} certificate, but connection is allowed to proceed. "
              + "Hostname: {}, Subject: {}, Issuer: {}, Valid from: {} to: {}, Reason: {}",
          type,
          hostname,
          cert.getSubjectX500Principal(),
          cert.getIssuerX500Principal(),
          cert.getNotBefore(),
          cert.getNotAfter(),
          e.getMessage());

      // Log additional details for debugging
      if (log.isDebugEnabled()) {
        log.debug("Full certificate validation error:", e);
      }

      recordValidationFailure(hostname, issuer, reason, type);
    }
  }

  private String getCertificateInfo(X509Certificate cert) {
    return String.format(
        "Subject: %s, Issuer: %s, Valid: %s to %s",
        cert.getSubjectX500Principal(),
        cert.getIssuerX500Principal(),
        cert.getNotBefore(),
        cert.getNotAfter());
  }

  /**
   * Extracts hostname from certificate CN or Subject Alternative Names.
   *
   * @param cert X509Certificate
   * @return hostname or "unknown"
   */
  private String extractHostname(X509Certificate cert) {
    // Try CN first
    String cn = extractCommonName(cert.getSubjectX500Principal().getName());
    if (!cn.equals("unknown")) {
      return cn;
    }

    // Try Subject Alternative Names
    try {
      Collection<List<?>> sans = cert.getSubjectAlternativeNames();
      if (sans != null) {
        for (List<?> san : sans) {
          // Type 2 is DNS name
          if (san.size() >= 2 && san.get(0) instanceof Integer && (Integer) san.get(0) == 2) {
            return san.get(1).toString();
          }
        }
      }
    } catch (Exception e) {
      log.debug("Could not extract SAN from certificate", e);
    }

    return "unknown";
  }

  /**
   * Extracts Common Name from X.500 distinguished name.
   *
   * @param dn Distinguished name (e.g., "CN=example.com,O=Company")
   * @return Common Name or "unknown"
   */
  private String extractCommonName(String dn) {
    Matcher matcher = CN_PATTERN.matcher(dn);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "unknown";
  }

  /**
   * Categorizes certificate validation failure into common reasons.
   *
   * @param e CertificateException
   * @return failure reason category
   */
  private String categorizeFailureReason(CertificateException e) {
    String message = e.getMessage().toLowerCase();

    if (message.contains("unable to find valid certification path")
        || message.contains("pkix path")) {
      return "untrusted_ca";
    } else if (message.contains("expired") || message.contains("notafter")) {
      return "certificate_expired";
    } else if (message.contains("not yet valid") || message.contains("notbefore")) {
      return "certificate_not_yet_valid";
    } else if (message.contains("hostname") || message.contains("certificate not valid")) {
      return "hostname_mismatch";
    } else if (message.contains("revoked")) {
      return "certificate_revoked";
    } else if (message.contains("self-signed")) {
      return "self_signed";
    } else {
      return "other";
    }
  }

  /**
   * Records certificate validation failure metric.
   *
   * @param hostname hostname from certificate
   * @param issuer certificate issuer
   * @param reason failure reason
   * @param certificateType client or server
   */
  private void recordValidationFailure(
      String hostname, String issuer, String reason, String certificateType) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of("hostname", hostname));
    tags.add(Tag.of("issuer", issuer));
    tags.add(Tag.of("reason", reason));
    tags.add(Tag.of("certificate_type", certificateType));
    tags.add(Tag.of("status", "failure"));

    meterRegistry.counter(METRIC_CERT_VALIDATION, tags).increment();
  }

  /**
   * Records certificate validation success metric.
   *
   * @param hostname hostname from certificate
   * @param issuer certificate issuer
   * @param certificateType client or server
   */
  private void recordValidationSuccess(String hostname, String issuer, String certificateType) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of("hostname", hostname));
    tags.add(Tag.of("issuer", issuer));
    tags.add(Tag.of("reason", "none"));
    tags.add(Tag.of("certificate_type", certificateType));
    tags.add(Tag.of("status", "success"));

    meterRegistry.counter(METRIC_CERT_VALIDATION, tags).increment();
  }
}
