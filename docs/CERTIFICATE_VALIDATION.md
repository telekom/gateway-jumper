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

## Migration Strategy

### Phase 1: Enable Warn Mode
```yaml
certificate-validation-mode: warn
```

1. Deploy to production with `warn` mode
2. Monitor logs for certificate validation warnings
3. Identify upstream services with certificate issues
4. Coordinate with upstream service owners to fix certificates

### Phase 2: Fix Certificate Issues

Common issues found in logs:
- **Self-signed certificates**: Replace with CA-signed certificates
- **Expired certificates**: Renew certificates before expiration
- **Hostname mismatch**: Ensure certificate CN/SAN matches hostname
- **Untrusted CA**: Add intermediate/root CA to system trust store

### Phase 3: Enable Strict Mode
```yaml
certificate-validation-mode: strict
```

1. After all warnings resolved in logs
2. Test in staging environment first
3. Deploy to production
4. Monitor for connection failures

## Monitoring and Alerting

### Prometheus Metrics

When using `warn` mode, Jumper automatically records detailed Prometheus metrics for certificate validation:

**Metrics Available:**
- `jumper.ssl.certificate.validation.failure` - Counter with tags: hostname, issuer, reason
- `jumper.ssl.certificate.validation.success` - Counter with tags: hostname, issuer

**Example Queries:**
```promql
# Top hosts with certificate issues
topk(10, sum by (hostname) (jumper_ssl_certificate_validation_failure_total))

# Validation failures by reason
sum by (reason) (jumper_ssl_certificate_validation_failure_total)

# Failure rate
rate(jumper_ssl_certificate_validation_failure_total[5m])
```

For detailed metric documentation, queries, and Grafana dashboards, see **[CERTIFICATE_METRICS.md](CERTIFICATE_METRICS.md)**.

### Log Patterns to Monitor

**Certificate warnings (warn mode):**
```
Certificate validation failed for server certificate
```

**Certificate validation mode:**
```
Certificate validation mode: (INSECURE|WARN|STRICT)
```

### Recommended Alerts

1. **High volume of certificate warnings**
   - Metric: `rate(jumper_ssl_certificate_validation_failure_total[5m]) > 0.1`
   - Indicates widespread certificate issues
   - Alert threshold: > 10% of upstream connections

2. **Certificate expiration warnings**
   - Metric: `jumper_ssl_certificate_validation_failure_total{reason="certificate_expired"} > 0`
   - Proactive alert before certificates expire

3. **Unexpected validation failures in strict mode**
   - Any SSL handshake failures
   - May indicate MITM attacks or infrastructure issues

See [CERTIFICATE_METRICS.md](CERTIFICATE_METRICS.md) for complete alerting rule examples.

## System Trust Store

### Adding Custom CA Certificates

If you need to trust additional CAs (e.g., internal enterprise CA):

#### Option 1: JVM Trust Store
```bash
keytool -import -alias mycompanyca \
  -keystore $JAVA_HOME/lib/security/cacerts \
  -file /path/to/ca-certificate.pem \
  -storepass changeit
```

#### Option 2: Container Image
Add CA certificates during image build:

```dockerfile
# Add custom CA certificate
COPY ca-certificates/*.crt /usr/local/share/ca-certificates/
RUN update-ca-certificates

# Import into Java trust store
RUN keytool -import -trustcacerts -noprompt \
    -alias mycompanyca \
    -file /usr/local/share/ca-certificates/mycompany-ca.crt \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit
```

#### Option 3: Environment Variable (Runtime)
```bash
export JAVAX_NET_SSL_TRUST_STORE=/path/to/custom-truststore.jks
export JAVAX_NET_SSL_TRUST_STORE_PASSWORD=changeit
```

## Security Considerations

### Development vs Production

| Environment | Recommended Mode | Rationale |
|-------------|-----------------|-----------|
| Local Dev | `insecure` | Self-signed certs common, convenience |
| Integration | `warn` | Identify issues early without blocking |
| Staging | `strict` | Mirror production security |
| Production | `strict` | Maximum security, trust only valid certs |

### Risk Assessment

**Insecure Mode Risks:**
- Man-in-the-middle (MITM) attacks
- Connection to compromised/spoofed services
- No verification of upstream identity
- Compliance violations

**Warn Mode Benefits:**
- Zero-downtime migration path
- Visibility into certificate health
- Gradual security hardening
- Audit trail for compliance

**Strict Mode Benefits:**
- Standard TLS security guarantees
- Protection against MITM attacks
- Compliance with security standards
- Clear failure on certificate issues

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

SSL/TLS handshake is typically 50-200ms, so validation adds <2% overhead.

## References

- [RFC 5280 - X.509 Certificate Specification](https://tools.ietf.org/html/rfc5280)
- [Java PKI Programmers Guide](https://docs.oracle.com/en/java/javase/21/security/java-pki-programmers-guide.html)
- [Spring Boot SSL Configuration](https://docs.spring.io/spring-boot/reference/features/ssl.html)
