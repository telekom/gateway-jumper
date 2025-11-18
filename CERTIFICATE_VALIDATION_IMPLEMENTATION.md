<!--
SPDX-FileCopyrightText: 2023 Deutsche Telekom AG

SPDX-License-Identifier: CC0-1.0    
-->

# Certificate Validation Implementation Summary

## Overview

Implementation adds configurable TLS/SSL certificate validation for upstream connections with three modes:
- **insecure** (default) - backward compatible, no validation
- **warn** - validate with warnings, connections allowed
- **strict** - enforce validation, reject invalid certificates

## Files Modified/Created

### New Files

1. **`src/main/java/jumper/config/WarningTrustManager.java`**
   - Custom X509TrustManager implementation
   - Validates certificates but logs warnings instead of failing
   - Allows observability without breaking connections

2. **`src/test/java/jumper/config/WarningTrustManagerTest.java`**
   - Unit tests for WarningTrustManager
   - Tests valid/invalid certificates, null/empty chains
   - Mockito-based tests

3. **`docs/CERTIFICATE_VALIDATION.md`**
   - Comprehensive documentation
   - Configuration guide
   - Migration strategy
   - Troubleshooting guide

### Modified Files

1. **`src/main/java/jumper/config/HttpClientConfiguration.java`**
   - Added imports for TrustManagerFactory, KeyStore, X509TrustManager
   - Added configuration property: `jumper.ssl.certificate-validation-mode`
   - Created `createTrustManager()` method with mode switching logic
   - Updated `createSslContextWithCustomizedCiphers()` to use configurable trust manager

2. **`src/main/resources/application.yml`**
   - Added new configuration section under `jumper.ssl`
   - Documented three validation modes
   - Default value: `insecure` (backward compatible)

## Configuration

### Environment Variable
```bash
JUMPER_SSL_CERTIFICATE_VALIDATION_MODE=warn
```

### Application Property
```yaml
jumper:
  ssl:
    certificate-validation-mode: warn  # insecure | warn | strict
```

## Implementation Details

### WarningTrustManager

The key innovation is the `WarningTrustManager` class that:

1. **Wraps** the default JVM trust manager
2. **Validates** certificates using standard PKI validation
3. **Catches** CertificateException when validation fails
4. **Logs WARNING** with detailed certificate information
5. **Allows** connection to proceed despite validation failure

```java
try {
  delegateTrustManager.checkServerTrusted(chain, authType);
  log.debug("Certificate validation successful");
} catch (CertificateException e) {
  log.warn("Certificate validation failed but connection allowed. Details: {}", e.getMessage());
  // Connection proceeds
}
```

### Trust Manager Selection

The `createTrustManager()` method selects trust manager based on mode:

| Mode | Trust Manager | Behavior |
|------|--------------|----------|
| `strict` | Default JVM TrustManager | Standard validation, fails on invalid cert |
| `warn` | WarningTrustManager(default) | Validates, logs warning, allows connection |
| `insecure` | InsecureTrustManagerFactory | No validation, accepts all certificates |

## Benefits

### 1. Zero-Downtime Migration
- Start with `insecure` (current behavior)
- Deploy with `warn` mode to production
- Monitor logs for certificate issues
- Fix certificate problems gradually
- Switch to `strict` when ready

### 2. Security Observability
- See which upstreams have certificate issues
- Identify expiring certificates proactively
- Audit certificate health across infrastructure
- No service disruption during investigation

### 3. Compliance & Auditing
- Demonstrate certificate validation attempts
- Log trail of certificate issues
- Evidence for security audits
- Gradual security hardening

### 4. Backward Compatibility
- Default mode is `insecure` (current behavior)
- No breaking changes
- Opt-in security enhancement
- Smooth migration path

## Validation Checks (warn & strict modes)

When validation is enabled, these checks are performed:

1. **Certificate Chain Trust**
   - Must be signed by CA in system trust store
   - Intermediate certificates must form valid chain

2. **Certificate Expiration**
   - Current date must be within validity period
   - Checks `notBefore` and `notAfter`

3. **Hostname Verification**
   - CN or SAN must match target hostname

4. **Revocation** (if configured)
   - Certificate not revoked by CA

## Example Log Output

### Warn Mode - Valid Certificate
```
INFO  Certificate validation mode: WARN - invalid certificates will be logged but connections allowed
DEBUG Certificate validation successful for Subject: CN=api.example.com, Issuer: CN=DigiCert, Valid: 2024-01-01 to: 2025-12-31
```

### Warn Mode - Invalid Certificate
```
INFO  Certificate validation mode: WARN - invalid certificates will be logged but connections allowed
WARN  Certificate validation failed for server certificate, but connection is allowed to proceed. 
      Subject: CN=api.internal.example.com, 
      Issuer: CN=Self-Signed CA, 
      Valid from: 2023-01-01 to: 2024-01-01, 
      Reason: unable to find valid certification path to requested target
```

### Strict Mode - Invalid Certificate
```
INFO  Certificate validation mode: STRICT - connections will fail on invalid certificates
ERROR Failed to connect to https://api.internal.example.com
Caused by: javax.net.ssl.SSLHandshakeException: 
  sun.security.validator.ValidatorException: PKIX path building failed
```

## Testing

### Unit Tests
- `WarningTrustManagerTest.java` covers all scenarios
- Mocked certificate validation
- Tests warning behavior (no exceptions thrown)

### Integration Testing
1. Set mode to `warn`
2. Connect to service with self-signed cert
3. Verify connection succeeds
4. Verify warning in logs

### Migration Testing
1. Deploy with `insecure` (baseline)
2. Switch to `warn` in production
3. Monitor for 7-30 days
4. Identify and fix certificate issues
5. Test `strict` in staging
6. Deploy `strict` to production

## Security Considerations

### Risk Levels

| Mode | MITM Risk | Compliance | Use Case |
|------|-----------|-----------|----------|
| insecure | **High** | ❌ Fails | Legacy/Development only |
| warn | **Medium** | ⚠️ Partial | Migration/Observability |
| strict | **Low** | ✅ Passes | Production (recommended) |

### Production Recommendation

**Short-term:** Use `warn` mode
- Provides visibility
- No service disruption
- Identifies certificate issues

**Long-term:** Use `strict` mode
- Standard TLS security
- Compliance with security standards
- Protection against MITM attacks

## Migration Timeline Example

```
Week 1-2:   Deploy with mode=warn, monitor logs
Week 3-4:   Analyze warnings, create remediation plan
Week 5-8:   Fix certificate issues on upstream services
Week 9:     Test mode=strict in staging
Week 10:    Deploy mode=strict to production
Week 11+:   Monitor, ensure no SSL handshake failures
```

## Troubleshooting

### Common Issues

**Issue:** High volume of warnings in warn mode
- **Cause:** Many upstreams with invalid certificates
- **Solution:** Prioritize fixing most-used services first

**Issue:** Connections fail in strict mode
- **Cause:** Invalid upstream certificates
- **Solution:** Switch back to warn, fix certificates, retry

**Issue:** Internal CA not trusted
- **Cause:** Internal CA not in system trust store
- **Solution:** Add CA certificate to JVM trust store

## References

- [RFC 5280 - X.509 Certificate Specification](https://tools.ietf.org/html/rfc5280)
- [Java PKI Documentation](https://docs.oracle.com/en/java/javase/21/security/java-pki-programmers-guide.html)
- [OWASP TLS Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html)

## Future Enhancements

Potential improvements for future iterations:

1. **Per-upstream configuration**
   - Allow different modes per upstream service
   - Configuration: `jumper.ssl.upstreams.api-xyz.mode=strict`

2. **Metrics/Prometheus**
   - Counter for certificate validation failures
   - Gauge for certificates expiring soon
   - Alert on validation failure spike

3. **Certificate expiry warnings**
   - Proactive logging for certificates expiring in 30/60/90 days
   - Separate log level or metric

4. **Custom trust store support**
   - Allow loading custom trust store file
   - Support for multiple trust stores

5. **Certificate pinning**
   - Pin specific certificates or public keys
   - Enhanced security for critical upstreams
