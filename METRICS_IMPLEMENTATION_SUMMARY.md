<!--
SPDX-FileCopyrightText: 2023 Deutsche Telekom AG

SPDX-License-Identifier: CC0-1.0    
-->

# Certificate Validation Metrics - Implementation Summary

## Overview

Enhanced the certificate validation implementation to include **Prometheus metrics** that track which upstream hosts have certificate issues, categorized by failure reason.

## What Was Added

### Metrics Instrumentation

**Two new Prometheus counters:**

1. **`jumper.ssl.certificate.validation.failure`**
   - Tracks certificate validation failures
   - Tags: `hostname`, `issuer`, `reason`, `certificate_type`, `result`

2. **`jumper.ssl.certificate.validation.success`**
   - Tracks successful validations
   - Tags: `hostname`, `issuer`, `certificate_type`, `result`

### Failure Reason Categorization

The implementation automatically categorizes validation failures:

| Reason | Description |
|--------|-------------|
| `untrusted_ca` | Certificate not signed by trusted CA |
| `certificate_expired` | Certificate expired |
| `certificate_not_yet_valid` | Certificate not yet valid |
| `hostname_mismatch` | Hostname doesn't match certificate |
| `certificate_revoked` | Certificate revoked |
| `self_signed` | Self-signed certificate |
| `no_certificate_chain` | No certificate chain provided |
| `other` | Other validation failure |

### Hostname Extraction

Automatically extracts hostname from:
1. Certificate Common Name (CN)
2. Subject Alternative Names (SAN) - DNS type
3. Falls back to "unknown" if not available

## Files Modified

### 1. `WarningTrustManager.java`
**Changes:**
- Added `MeterRegistry` dependency
- Added hostname extraction logic (CN and SAN parsing)
- Added failure reason categorization
- Added metric recording methods
- Records metrics for both success and failure cases

**New Methods:**
- `extractHostname()` - Extracts hostname from certificate
- `extractCommonName()` - Parses CN from DN
- `categorizeFailureReason()` - Categorizes exception into reason
- `recordValidationFailure()` - Records failure metric
- `recordValidationSuccess()` - Records success metric

### 2. `HttpClientConfiguration.java`
**Changes:**
- Added `MeterRegistry` as constructor dependency
- Pass `MeterRegistry` to `WarningTrustManager` constructor

### 3. `WarningTrustManagerTest.java`
**Changes:**
- Added `SimpleMeterRegistry` to test setup
- Pass registry to `WarningTrustManager` in tests

### 4. New Documentation
- **`docs/CERTIFICATE_METRICS.md`** - Comprehensive metrics guide
  - Metric definitions
  - PromQL query examples
  - Grafana dashboard examples
  - Alert rule examples
  - Best practices

## Example Metrics Output

```
# HELP jumper_ssl_certificate_validation_failure_total Certificate validation failures with warnings
# TYPE jumper_ssl_certificate_validation_failure_total counter
jumper_ssl_certificate_validation_failure_total{
  certificate_type="server",
  hostname="api.internal.example.com",
  issuer="Self-Signed CA",
  reason="untrusted_ca",
  result="failure"
} 42.0

jumper_ssl_certificate_validation_failure_total{
  certificate_type="server",
  hostname="old-api.example.com",
  issuer="DigiCert",
  reason="certificate_expired",
  result="failure"
} 15.0

# HELP jumper_ssl_certificate_validation_success_total Certificate validation successes
# TYPE jumper_ssl_certificate_validation_success_total counter
jumper_ssl_certificate_validation_success_total{
  certificate_type="server",
  hostname="api.example.com",
  issuer="DigiCert",
  result="success"
} 1523.0
```

## Usage Examples

### Query Top Problematic Hosts
```promql
topk(10, sum by (hostname) (jumper_ssl_certificate_validation_failure_total))
```

### Query Failures by Reason
```promql
sum by (reason) (jumper_ssl_certificate_validation_failure_total)
```

### Calculate Failure Rate
```promql
rate(jumper_ssl_certificate_validation_failure_total[5m])
```

### Calculate Success Rate Percentage
```promql
sum(jumper_ssl_certificate_validation_success_total) 
/ 
(sum(jumper_ssl_certificate_validation_failure_total) + sum(jumper_ssl_certificate_validation_success_total)) 
* 100
```

## Alert Example

```yaml
- alert: HighCertificateValidationFailureRate
  expr: |
    sum(rate(jumper_ssl_certificate_validation_failure_total[5m])) 
    / 
    (sum(rate(jumper_ssl_certificate_validation_failure_total[5m])) + sum(rate(jumper_ssl_certificate_validation_success_total[5m]))) 
    > 0.10
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High certificate validation failure rate"
    description: "{{ $value | humanizePercentage }} of certificate validations are failing"
```

## Benefits

### 1. **Precise Tracking**
- Know exactly which hosts have issues
- Categorized failure reasons for quick diagnosis
- Historical tracking of certificate problems

### 2. **Proactive Monitoring**
- Identify certificate issues before they cause outages
- Track expiring certificates
- Monitor success/failure trends

### 3. **Migration Support**
- Track progress fixing certificate issues
- Identify high-priority hosts (most connections)
- Validate before enabling strict mode

### 4. **Operational Insights**
- Dashboards showing certificate health across infrastructure
- Alerting on new certificate issues
- Compliance and audit trail

### 5. **Better Than Logs**
- **Aggregatable** - Sum, rate, topk queries
- **Queryable** - Complex queries across time ranges
- **Visual** - Grafana dashboards and graphs
- **Alertable** - Prometheus alerting rules
- **Historical** - Long-term trending and analysis

## Grafana Dashboard Example

Recommended dashboard structure:

**Row 1: Overview**
- Total validations (stat)
- Success rate (gauge, 0-100%)
- Failure rate (gauge)
- Unique hosts with issues (stat)

**Row 2: Failure Analysis**
- Failures by reason (pie chart)
- Top 10 problematic hosts (bar chart)
- Failure rate timeline (graph)

**Row 3: Details**
- Failures by hostname (table)
- Recent failures heatmap
- Success vs failure comparison (graph)

## Performance Impact

**Minimal:**
- Metrics recorded only during certificate validation (TLS handshake)
- Counter increment is O(1) operation
- Hostname extraction happens once per connection
- No additional network calls or blocking operations

**Cardinality:**
- ~100-1000 unique hostnames typical
- 8 possible failure reasons
- Expected: ~800-8000 time series (manageable)

## Testing

Metrics can be verified:

1. **Manual inspection:**
```bash
curl http://localhost:8080/actuator/prometheus | grep jumper_ssl_certificate
```

2. **Unit tests:**
- Verify metrics recorded on validation failure
- Verify metrics recorded on validation success
- Verify hostname extraction
- Verify reason categorization

3. **Integration tests:**
- Connect to service with invalid cert
- Verify metric incremented with correct tags

## Next Steps

1. **Deploy with `warn` mode** - Start collecting metrics
2. **Create Grafana dashboards** - Visualize certificate health
3. **Set up alerts** - Get notified of issues
4. **Review metrics weekly** - Track problematic hosts
5. **Fix certificate issues** - Work with service owners
6. **Enable `strict` mode** - After metrics show no issues

## Documentation

- **[CERTIFICATE_VALIDATION.md](docs/CERTIFICATE_VALIDATION.md)** - Main configuration guide
- **[CERTIFICATE_METRICS.md](docs/CERTIFICATE_METRICS.md)** - Detailed metrics documentation

## References

- [Prometheus Counter Documentation](https://prometheus.io/docs/concepts/metric_types/#counter)
- [Micrometer Documentation](https://micrometer.io/docs)
- [PromQL Query Examples](https://prometheus.io/docs/prometheus/latest/querying/examples/)
