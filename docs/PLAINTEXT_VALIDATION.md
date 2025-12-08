<!--
SPDX-FileCopyrightText: 2023 Deutsche Telekom AG

SPDX-License-Identifier: CC0-1.0    
-->

# Plaintext HTTP Validation Configuration

## Overview

Jumper supports three modes for validating plaintext HTTP connections to upstream services:

1. **Insecure** (default) - Allows all plaintext HTTP connections without warnings
2. **Warn** - Detects plaintext HTTP but allows connections with warnings
3. **Strict** - Blocks plaintext HTTP connections, enforces HTTPS-only

This feature helps organizations migrate from plaintext HTTP to encrypted HTTPS connections by providing observability and optionally enforcing HTTPS-only policies.

## Configuration

### Environment Variable
```bash
export JUMPER_SSL_PLAINTEXT_VALIDATION_MODE=warn
```

### Application Property
```yaml
jumper:
  ssl:
    plaintext-validation-mode: warn
```

## Validation Modes

### Insecure Mode (Default)
```yaml
plaintext-validation-mode: insecure
```

**Behavior:**
- Allows **all plaintext HTTP** connections without any warnings
- No logs or metrics recorded for plaintext connections
- **Backwards compatible** (current default behavior)
- Minimal performance overhead (early exit in filter)

**Use Cases:**
- Development environments
- Legacy systems requiring HTTP
- Backwards compatibility (current default behavior)

**Log Output:**
- No logs generated for HTTP connections
- HTTPS connections pass through silently

---

### Warn Mode (Recommended for Migration)
```yaml
plaintext-validation-mode: warn
```

**Behavior:**
- Detects plaintext HTTP connections
- Logs **warnings** when plaintext HTTP is detected
- **Allows connection to proceed** despite using plaintext
- Records Prometheus metrics for tracking
- Provides observability without breaking existing connections

**Use Cases:**
- Production environments migrating to HTTPS-only
- Identifying which upstreams still use plaintext HTTP
- Audit and compliance requirements
- Security posture assessment

**Warning Log Example:**
```
WARN  Plaintext HTTP connection detected, but connection is allowed to proceed. 
Target: http://api.internal.example.com/users, Host: api.internal.example.com, Path: /users. 
Consider upgrading to HTTPS for security.
```

**Metric Recorded:**
```
jumper_ssl_plaintext_connection_total{
  hostname="api.internal.example.com",
  protocol="http",
  action="warned",
  validation_mode="warn"
} 1
```

---

### Strict Mode (Production Recommended)
```yaml
plaintext-validation-mode: strict
```

**Behavior:**
- Detects plaintext HTTP connections
- **Blocks connections** with HTTP 502 Bad Gateway error
- Logs error messages
- Records Prometheus metrics
- Enforces HTTPS-only policy

**Use Cases:**
- Production environments with zero-trust security policies
- Compliance requirements (PCI-DSS, HIPAA, SOC2)
- Organizations that have completed HTTP to HTTPS migration
- Maximum security posture

**Error Log Example:**
```
ERROR Plaintext HTTP connection blocked in strict mode. 
Target: http://api.internal.example.com/users, Host: api.internal.example.com, Path: /users. 
Only HTTPS connections are allowed.
```

**Error Response to Client:**
```
HTTP/1.1 502 Bad Gateway
Content-Type: text/plain

Plaintext HTTP connection blocked by gateway security policy. Only HTTPS connections are allowed.
```

**Metric Recorded:**
```
jumper_ssl_plaintext_connection_total{
  hostname="api.internal.example.com",
  protocol="http",
  action="blocked",
  validation_mode="strict"
} 1
```

## Combination with Certificate Validation

Plaintext validation works independently but complements certificate validation:

### Recommended Combined Configuration

**Maximum Security (Production):**
```yaml
jumper:
  ssl:
    certificate-validation-mode: strict      # Enforce valid certificates
    plaintext-validation-mode: strict        # Enforce HTTPS-only
```

**Migration Phase (Observability):**
```yaml
jumper:
  ssl:
    certificate-validation-mode: warn        # Log certificate issues
    plaintext-validation-mode: warn          # Log plaintext HTTP usage
```

**Development (Permissive):**
```yaml
jumper:
  ssl:
    certificate-validation-mode: insecure    # Allow self-signed certs
    plaintext-validation-mode: insecure      # Allow HTTP
```

## References

- [OWASP Transport Layer Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html)
- [RFC 2818 - HTTP Over TLS](https://tools.ietf.org/html/rfc2818)
- [Mozilla TLS Configuration](https://wiki.mozilla.org/Security/Server_Side_TLS)
- [Certificate Validation Documentation](CERTIFICATE_VALIDATION.md)
