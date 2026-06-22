# Use Case: Export Metrics to Observability Stack

## Overview

**Use Case ID:** UC-089
**Use Case Name:** Export Metrics to Observability Stack
**Primary Actor:** Operator
**Goal:** Expose collected application metrics (UC-088) on a dedicated HTTP endpoint in a format that an external metrics scraper can ingest, so that the operator can integrate LinkWeave into their existing observability infrastructure.
**Status:** Draft

## Traceability

**Maps to:** FR-089, NFR-025, C-021, C-022

---

## Preconditions

- The Quarkus application is running with `quarkus-micrometer-registry-prometheus` on the classpath.
- Application metrics are registered as described in UC-088.

## Main Success Scenario — Prometheus-Compatible Scraping

1. Operator configures an external metrics scraper (e.g., Prometheus, VictoriaMetrics vmagent) to scrape the LinkWeave metrics endpoint at the configured path (default `/q/metrics`).
2. Operator sets the scrape interval (e.g., 15 seconds) in the scraper configuration.
3. Scraper sends an HTTP `GET` request to the LinkWeave metrics endpoint.
4. System collects all registered meter snapshots from the Micrometer `MeterRegistry`.
5. System serializes the meters into Prometheus exposition format text.
6. System returns the response with HTTP 200 and `Content-Type: text/plain; version=0.0.4; charset=utf-8`.
7. Scraper parses the response and stores the time-series data.
8. Repeat from step 3 on the configured scrape interval.

## Main Success Scenario — Operator Verifies Endpoint Manually

1. Operator opens a browser or uses `curl` to access the metrics endpoint (e.g., `http://localhost:8443/q/metrics`).
2. System returns the Prometheus exposition format text showing all registered metrics with their current values.
3. Operator confirms the expected metrics are present (HTTP request metrics, JVM metrics, custom `linkweave.*` business metrics).

## Alternative Flows

### A1: Metrics Endpoint Without Authentication (Default)

**Trigger:** Operator has not configured authentication on the metrics endpoint.
**Flow:**

1. Scraper or operator accesses `/q/metrics` without credentials.
2. System returns the metrics response without requiring authentication.
3. This is the default and recommended configuration when the endpoint is only accessible from an internal/trusted network.

### A2: Metrics Endpoint With Authentication

**Trigger:** Operator has configured the metrics endpoint to require authentication (e.g., the application is exposed to the internet).
**Flow:**

1. Scraper sends `GET /q/metrics` without credentials.
2. System responds with HTTP 401.
3. Operator configures the scraper with appropriate authentication credentials (basic auth, bearer token, or mTLS depending on the Quarkus security configuration).
4. Scraper retries with credentials.
5. System validates credentials and returns the metrics response.

### A3: Metrics Disabled

**Trigger:** Operator has set `quarkus.micrometer.enabled=false` in the application configuration.
**Flow:**

1. Scraper sends `GET /q/metrics`.
2. System responds with HTTP 404 (endpoint not registered).
3. Operator must re-enable metrics via configuration and restart the application.

### A4: No Scraper Configured

**Trigger:** The application is running with metrics enabled but no external scraper is configured.
**Flow:**

1. Metrics accumulate in the in-memory `MeterRegistry`.
2. The `/q/metrics` endpoint remains available and returns current values on demand.
3. No data is lost — gauges and counters reflect the current state at the time of any request.
4. Timers and distribution summaries retain only recent data within their configured decay window.

### A5: Scraper Request Times Out

**Trigger:** The metrics endpoint takes longer than the scraper's configured timeout to respond (e.g., extremely high metric count).
**Flow:**

1. Scraper cancels the request and logs a scrape failure.
2. System continues operating normally.
3. Next scrape interval succeeds.
4. If timeouts persist, the operator should investigate cardinality (BR-001 in UC-088) and consider reducing registered meters.

### A6: Multiple Scrapers

**Trigger:** More than one scraper is configured to scrape the same endpoint (e.g., Prometheus + a debugging tool).
**Flow:**

1. Each scraper independently requests `/q/metrics`.
2. System responds to each request independently with the current meter snapshots.
3. No conflict — the endpoint is read-only and stateless from the scraper's perspective.

## Postconditions

### Success Postconditions

- The `/q/metrics` endpoint is accessible and returns all registered metrics in Prometheus exposition format.
- The external scraper has successfully ingested the metrics data.
- Metrics are updated on every scrape with fresh values.

### Failure Postconditions

- The metrics endpoint returns an error or is unreachable.
- The scraper logs a failure and retries on the next interval.
- No application data is affected — metrics export is non-critical infrastructure.

## Business Rules

### BR-001: Endpoint Path Is Configurable

The metrics endpoint path defaults to `/q/metrics` but can be changed via `quarkus.micrometer.export.prometheus.path` in `application.properties`. This allows operators to avoid conflicts with other services behind a reverse proxy.

### BR-002: Export Format Is Prometheus by Default

The default export format is Prometheus exposition text (`text/plain; version=0.0.4`). This format is compatible with:

- Prometheus
- VictoriaMetrics (vmagent)
- Thanos
- Cortex
- Mimir
- Telegraf (Prometheus input plugin)
- Netdata (Prometheus remote read)

Additional formats (JSON, OTLP) may be enabled via configuration properties, but Prometheus format must always be available when metrics are enabled.

### BR-003: Endpoint Response Time Budget

The metrics endpoint must respond within 500 ms under normal load. If the response time exceeds this budget, the operator should investigate:

1. Cardinality explosion (too many unique tag combinations — see UC-088 BR-001).
2. Expensive gauge evaluation (e.g., a database query inside a gauge callback).

### BR-004: Metrics Endpoint Does Not Impact Business Endpoints

The metrics endpoint runs on the same HTTP server as business API endpoints. Serialization of metrics must be performed synchronously on the request thread but must not hold locks or block on external resources. Gauge callbacks that query the database must use a pre-computed or cached value, not execute a live query on every scrape.

---

## Configuration

| Property | Default | Description |
|---|---|---|
| `quarkus.micrometer.export.prometheus.path` | `/q/metrics` | HTTP path for the Prometheus metrics endpoint |
| `quarkus.micrometer.export.prometheus.enabled` | `true` | Enable the Prometheus export endpoint |
| `quarkus.micrometer.export.json.enabled` | `false` | Also expose metrics in JSON format at `/q/metrics/json` |

### Example Scraper Configuration (Prometheus)

```yaml
scrape_configs:
  - job_name: 'linkweave'
    scrape_interval: 15s
    static_configs:
      - targets: ['linkweave:8443']
    scheme: https
    tls_config:
      insecure_skip_verify: true  # For self-signed certs in dev
```

### Example Scraper Configuration (VictoriaMetrics vmagent)

```yaml
scrape_configs:
  - job_name: 'linkweave'
    scrape_interval: 15s
    static_configs:
      - targets: ['linkweave:8443']
    scheme: https
    tls_config:
      insecure_skip_verify: true
```

---

## Reference

### Related Requirements

| ID | Title | Relationship |
|---|---|---|
| FR-089 | Metrics Export Endpoint | This use case implements FR-089 |
| FR-088 | Application Metrics Collection | Depends on meters from UC-088 |
| FR-090 | Metrics Dashboard | Dashboard consumes data scraped from this endpoint |
| NFR-025 | Metrics Endpoint Response Time | Response time budget |
| C-021 | Metrics via Micrometer | Technology constraint |
| C-022 | Observability Stack is Pluggable | Scraper choice is operator's decision |

### Related Use Cases

| ID | Title | Relationship |
|---|---|---|
| UC-088 | Collect Application Metrics | Provides the meters exported by this use case |
| UC-090 | Visualize Metrics in Dashboard | Dashboard queries data scraped from this endpoint |
