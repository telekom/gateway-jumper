<!--
SPDX-FileCopyrightText: 2023 Deutsche Telekom AG

SPDX-License-Identifier: CC0-1.0    
-->

# Certificate Validation Metrics

## Overview

When using `warn` mode for certificate validation, Jumper records detailed Prometheus metrics for every certificate validation attempt. This enables tracking certificate issues per upstream host and identifying patterns in certificate failures.

## Metrics

### `jumper.ssl.certificate.validation.failure`

Counter tracking certificate validation failures.

**Type:** Counter  
**Description:** Certificate validation failures with warnings  

**Tags:**
- `hostname` - Hostname extracted from certificate CN or SAN (e.g., `api.example.com`)
- `issuer` - Certificate issuer CN (e.g., `DigiCert`, `Self-Signed CA`)
- `reason` - Categorized failure reason (see below)
- `certificate_type` - `client` or `server`
- `result` - Always `failure` for this metric

**Failure Reasons:**
- `untrusted_ca` - Certificate not signed by trusted CA
- `certificate_expired` - Certificate expired (past notAfter date)
- `certificate_not_yet_valid` - Certificate not yet valid (before notBefore date)
- `hostname_mismatch` - Certificate hostname doesn't match target
- `certificate_revoked` - Certificate has been revoked
- `self_signed` - Self-signed certificate
- `no_certificate_chain` - No certificate provided
- `other` - Other validation failure

### `jumper.ssl.certificate.validation.success`

Counter tracking successful certificate validations.

**Type:** Counter  
**Description:** Certificate validation successes  

**Tags:**
- `hostname` - Hostname extracted from certificate CN or SAN
- `issuer` - Certificate issuer CN
- `certificate_type` - `client` or `server`
- `result` - Always `success` for this metric

## Example Queries

### Prometheus / PromQL

#### Total validation failures by hostname
```promql
sum by (hostname) (jumper_ssl_certificate_validation_failure_total)
```

#### Failure rate by hostname
```promql
rate(jumper_ssl_certificate_validation_failure_total[5m])
```

#### Validation failures grouped by reason
```promql
sum by (reason) (jumper_ssl_certificate_validation_failure_total)
```

#### Top 10 hosts with certificate issues
```promql
topk(10, sum by (hostname) (jumper_ssl_certificate_validation_failure_total))
```

#### Percentage of failed validations
```promql
sum(jumper_ssl_certificate_validation_failure_total) 
/ 
(sum(jumper_ssl_certificate_validation_failure_total) + sum(jumper_ssl_certificate_validation_success_total)) 
* 100
```

#### Hosts with expired certificates
```promql
sum by (hostname) (jumper_ssl_certificate_validation_failure_total{reason="certificate_expired"})
```

#### Hosts with untrusted CA certificates
```promql
sum by (hostname) (jumper_ssl_certificate_validation_failure_total{reason="untrusted_ca"})
```

#### Validation failures for specific host
```promql
sum by (reason) (jumper_ssl_certificate_validation_failure_total{hostname="api.example.com"})
```

### Grafana Dashboard Panels

#### Panel 1: Certificate Validation Failure Rate (Graph)
```promql
sum(rate(jumper_ssl_certificate_validation_failure_total[5m])) by (hostname)
```

**Panel Type:** Time series  
**Legend:** `{{hostname}}`

#### Panel 2: Validation Failures by Reason (Pie Chart)
```promql
sum by (reason) (jumper_ssl_certificate_validation_failure_total)
```

**Panel Type:** Pie chart  
**Legend:** `{{reason}}`

#### Panel 3: Top Problematic Hosts (Bar Gauge)
```promql
topk(20, sum by (hostname) (jumper_ssl_certificate_validation_failure_total))
```

**Panel Type:** Bar gauge (horizontal)  
**Display:** Value + name

#### Panel 4: Certificate Validation Success Rate (Stat)
```promql
sum(jumper_ssl_certificate_validation_success_total) 
/ 
(sum(jumper_ssl_certificate_validation_failure_total) + sum(jumper_ssl_certificate_validation_success_total)) 
* 100
```

**Panel Type:** Stat  
**Unit:** Percent (0-100)  
**Thresholds:** Green > 95%, Yellow 90-95%, Red < 90%

#### Panel 5: Certificate Issues Timeline (Heatmap)
```promql
sum(increase(jumper_ssl_certificate_validation_failure_total[1h])) by (hostname, reason)
```

**Panel Type:** Heatmap  
**Data format:** Time series buckets

## Alerting Rules

### Alert: High Certificate Validation Failure Rate

Triggers when more than 10% of validations fail.

```yaml
groups:
  - name: certificate_validation
    interval: 30s
    rules:
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

### Alert: Certificate Expired on Upstream

Triggers when expired certificates detected.

```yaml
      - alert: ExpiredCertificatesDetected
        expr: |
          sum by (hostname) (increase(jumper_ssl_certificate_validation_failure_total{reason="certificate_expired"}[5m])) > 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Expired certificate detected for {{ $labels.hostname }}"
          description: "Upstream host {{ $labels.hostname }} has an expired certificate"
```

### Alert: Many Hosts with Certificate Issues

Triggers when more than 5 hosts have certificate problems.

```yaml
      - alert: ManyHostsWithCertificateIssues
        expr: |
          count(sum by (hostname) (increase(jumper_ssl_certificate_validation_failure_total[10m]) > 0)) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Multiple upstream hosts have certificate issues"
          description: "{{ $value }} upstream hosts are experiencing certificate validation failures"
```

### Alert: New Certificate Issue Detected

Triggers when a host that previously validated successfully starts failing.

```yaml
      - alert: NewCertificateIssueDetected
        expr: |
          (sum by (hostname) (increase(jumper_ssl_certificate_validation_failure_total[5m])) > 0)
          unless
          (sum by (hostname) (increase(jumper_ssl_certificate_validation_failure_total[30m] offset 30m)) > 0)
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "New certificate issue detected for {{ $labels.hostname }}"
          description: "Host {{ $labels.hostname }} started experiencing certificate validation failures"
```

## Metric Collection Examples

### cURL from Prometheus Endpoint

```bash
curl -s http://localhost:8080/actuator/prometheus | grep jumper_ssl_certificate
```

**Example Output:**
```
# HELP jumper_ssl_certificate_validation_failure_total Certificate validation failures with warnings
# TYPE jumper_ssl_certificate_validation_failure_total counter
jumper_ssl_certificate_validation_failure_total{certificate_type="server",hostname="api.internal.example.com",issuer="Self-Signed CA",reason="untrusted_ca",result="failure",} 42.0
jumper_ssl_certificate_validation_failure_total{certificate_type="server",hostname="old-api.example.com",issuer="DigiCert",reason="certificate_expired",result="failure",} 15.0

# HELP jumper_ssl_certificate_validation_success_total Certificate validation successes
# TYPE jumper_ssl_certificate_validation_success_total counter
jumper_ssl_certificate_validation_success_total{certificate_type="server",hostname="api.example.com",issuer="DigiCert",result="success",} 1523.0
```

### Parsing with jq

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep jumper_ssl_certificate_validation_failure \
  | grep -v "^#" \
  | awk '{print $1, $2}' \
  | sort -k2 -rn \
  | head -10
```

## Integration with Monitoring Stack

### Prometheus Configuration

Add Jumper as a scrape target:

```yaml
scrape_configs:
  - job_name: 'jumper'
    scrape_interval: 30s
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['jumper:8080']
```

### Grafana Dashboard JSON

Import dashboard using ID or create custom panels with queries above.

**Recommended Dashboard Structure:**
1. Row: Overview
   - Total validation attempts
   - Success rate
   - Failure rate
   - Unique hosts with issues
2. Row: Failure Analysis
   - Failures by reason (pie chart)
   - Failures by hostname (bar chart)
   - Failure rate timeline (graph)
3. Row: Top Problematic Hosts
   - Table with hostname, reason, count
   - Heatmap of issues over time
4. Row: Recent Issues
   - Recent validation failures (logs panel)
   - New failures detected (stat)

## Use Cases

### 1. Migration Planning

**Goal:** Identify which upstreams need certificate fixes before enabling strict mode.

**Query:**
```promql
sort_desc(sum by (hostname, reason) (jumper_ssl_certificate_validation_failure_total))
```

**Action:** Create tickets for each hostname with failures, assign to service owners.

### 2. Certificate Expiration Tracking

**Goal:** Proactively identify expiring certificates.

**Query:**
```promql
sum by (hostname) (jumper_ssl_certificate_validation_failure_total{reason="certificate_expired"})
```

**Action:** Alert service owners to renew certificates.

### 3. Security Audit

**Goal:** Identify upstreams using self-signed or untrusted certificates.

**Query:**
```promql
sum by (hostname, issuer) (
  jumper_ssl_certificate_validation_failure_total{reason=~"untrusted_ca|self_signed"}
)
```

**Action:** Document security exceptions or require proper certificates.

### 4. Rollout Monitoring

**Goal:** Monitor certificate validation during deployment or configuration changes.

**Query:**
```promql
sum(increase(jumper_ssl_certificate_validation_failure_total[5m]))
```

**Action:** Rollback if failures spike unexpectedly.

## Cardinality Considerations

### Expected Cardinality

With 100 unique upstream hosts:
- Success metric: ~100 unique time series (1 per host)
- Failure metric: ~800 unique time series (100 hosts × 8 reasons, worst case)
- **Total:** ~900 time series

### Cardinality Management

If cardinality becomes an issue:

1. **Aggregate by reason only** (drop hostname tag for some queries)
2. **Use recording rules** to pre-aggregate
3. **Sample metrics** (reduce scrape frequency)
4. **Drop low-value labels** (e.g., certificate_type if always "server")

### Recording Rules (Optional)

Pre-aggregate metrics to reduce query load:

```yaml
groups:
  - name: certificate_validation_aggregates
    interval: 60s
    rules:
      - record: jumper:ssl_validation_failure_rate:5m
        expr: |
          rate(jumper_ssl_certificate_validation_failure_total[5m])
      
      - record: jumper:ssl_validation_failure_by_reason:sum
        expr: |
          sum by (reason) (jumper_ssl_certificate_validation_failure_total)
```

## Troubleshooting

### Metric Not Appearing

**Issue:** Metrics not visible in Prometheus.

**Checks:**
1. Verify validation mode is `warn`: Check logs for "Certificate validation mode: WARN"
2. Ensure upstream HTTPS connections occurring
3. Check Prometheus scrape config
4. Verify `/actuator/prometheus` endpoint accessible

### High Cardinality

**Issue:** Too many unique metric combinations.

**Solutions:**
1. Enable recording rules
2. Use relabeling to aggregate similar hostnames
3. Consider dropping issuer tag if not needed

### Missing Hostname

**Issue:** Metrics show `hostname="unknown"`.

**Cause:** Certificate lacks CN and SAN fields.

**Solution:** This is correct behavior for malformed certificates. Track separately and investigate cert structure.

## Best Practices

1. **Start with aggregated views** - Look at totals before drilling into specific hosts
2. **Set up alerts early** - Don't wait for manual inspection
3. **Review weekly** - Schedule regular metric reviews during migration
4. **Document exceptions** - Track known issues with self-signed certs
5. **Archive historical data** - Keep metrics for compliance/audit trail
6. **Test in staging first** - Validate queries work before production
7. **Create runbooks** - Document response procedures for alerts

## References

- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/best-practices/)
- [Micrometer Documentation](https://micrometer.io/docs)
