<!--
SPDX-FileCopyrightText: 2023 Deutsche Telekom AG

SPDX-License-Identifier: CC0-1.0    
-->

# Certificate Validation Configuration

## Overview

Jumper supports three modes for validating TLS/SSL certificates when connecting to upstream services:

1. **Insecure** (default) - Accepts all certificates without validation
2. **Warn** - Validates certificates but allows connections with warnings
3. **Strict** - Enforces strict validation, rejects invalid certificates

## Configuration

### Environment Variable
```bash
export JUMPER_SSL_CERTIFICATE_VALIDATION_MODE=warn
```

### Application Property
```yaml
jumper:
  ssl:
    certificate-validation-mode: warn
```

## Validation Modes

### Insecure Mode (Default)
```yaml
certificate-validation-mode: insecure
```

**Behavior:**
- Accepts **all certificates** without any validation
- No warnings or errors logged
- **Not recommended for production**

**Use Cases:**
- Development environments
- Testing with self-signed certificates
- Backwards compatibility (current default behavior)

**Log Output:**
```
Certificate validation mode: INSECURE - all certificates accepted without validation (NOT RECOMMENDED FOR PRODUCTION)
```

---

### Warn Mode (Recommended for Migration)
```yaml
certificate-validation-mode: warn
```

**Behavior:**
- Validates certificates against system CA certificates
- Logs **warnings** when validation fails
- **Allows connection to proceed** despite validation failure
- Provides observability without breaking existing connections

**Use Cases:**
- Production environments migrating to strict validation
- Identifying certificate issues without service disruption
- Audit and compliance requirements

**Warning Log Example:**
```
WARN  Certificate validation failed for server certificate, but connection is allowed to proceed. 
Subject: CN=example.com, Issuer: CN=Self-Signed, Valid from: 2024-01-01 to: 2025-01-01, 
Reason: unable to find valid certification path to requested target
```

**Success Log Example:**
```
DEBUG Certificate validation successful for Subject: CN=example.com, Issuer: CN=DigiCert, Valid: 2024-01-01 to: 2025-12-31
```

---

### Strict Mode (Production Recommended)
```yaml
certificate-validation-mode: strict
```

**Behavior:**
- Validates certificates against system CA certificates
- **Rejects connections** with invalid certificates
- Standard SSL/TLS security behavior

**Use Cases:**
- Production environments with proper PKI infrastructure
- Maximum security posture
- Compliance requirements (PCI-DSS, SOC2, etc.)

**Log Output:**
```
Certificate validation mode: STRICT - connections will fail on invalid certificates
```

**Error on Invalid Certificate:**
Connection will fail with SSL handshake error.

---

## Certificate Validation Checks

When validation is enabled (warn or strict mode), the following checks are performed:

1. **Certificate Chain of Trust**
   - Certificate must be signed by a trusted CA in the system trust store
   - Intermediate certificates must form valid chain to root CA

2. **Certificate Expiration**
   - Current date must be within certificate's validity period
   - Checks both `notBefore` and `notAfter` dates

3. **Hostname Verification**
   - Certificate Common Name (CN) or Subject Alternative Name (SAN) must match target hostname

4. **Certificate Revocation** (if configured at JVM level)
   - Certificate must not be revoked by CA

## Troubleshooting

### Issue: Warnings in logs but connections work (warn mode)

**Diagnosis:**
- Review full warning message for certificate details
- Check certificate expiration dates
- Verify hostname matches certificate CN/SAN

**Resolution:**
- Contact upstream service owner
- Request proper certificate from trusted CA

### Issue: Connection failures after enabling strict mode

**Diagnosis:**
```
Caused by: sun.security.validator.ValidatorException: PKIX path building failed
Caused by: javax.net.ssl.SSLHandshakeException: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
```

**Resolution:**
1. Temporarily switch back to `warn` mode to restore service
2. Analyze warnings to identify root cause
3. Fix certificate issues (see Phase 2 above)
4. Re-enable strict mode

### Issue: Internal CA certificates not trusted

**Symptoms:**
- Validation failures for internal services
- Works with insecure/warn mode

**Resolution:**
- Add internal CA to system trust store (see above)
- Ensure all intermediate certificates present in chain

## Performance Impact

Certificate validation has minimal performance impact:

- **Insecure mode**: No validation overhead
- **Warn mode**: Validation performed, ~1-2ms per connection (cached)
- **Strict mode**: Same overhead as warn mode

## References

- [RFC 5280 - X.509 Certificate Specification](https://tools.ietf.org/html/rfc5280)
- [Java PKI Programmers Guide](https://docs.oracle.com/en/java/javase/21/security/java-pki-programmers-guide.html)
- [Spring Boot SSL Configuration](https://docs.spring.io/spring-boot/reference/features/ssl.html)
