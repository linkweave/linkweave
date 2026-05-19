# Use Case: Visualize Metrics in Dashboard

## Overview

**Use Case ID:** UC-090
**Use Case Name:** Visualize Metrics in Dashboard
**Primary Actor:** Operator
**Goal:** Connect an external dashboarding tool (e.g., Grafana) to the metrics storage backend that is scraping Chainlink's metrics endpoint (UC-089), so that the operator can visualize system health, request rates, error rates, and business metrics over time through interactive charts and alerts.
**Status:** Draft

## Traceability

**Maps to:** FR-090, C-022

---

## Preconditions

- The Chainlink metrics endpoint (UC-089) is accessible and being scraped by a metrics storage backend (e.g., Prometheus, VictoriaMetrics).
- A dashboarding tool (e.g., Grafana) is deployed and accessible to the operator.
- The dashboarding tool can query the metrics storage backend (e.g., Grafana has a Prometheus/VictoriaMetrics data source configured).

## Main Success Scenario — Import Provided Dashboard

1. Operator opens the Grafana UI and navigates to Dashboards → Import.
2. Operator uploads the provided Chainlink dashboard JSON file from the project repository (`docs/monitoring/grafana-chainlink-dashboard.json`).
3. Operator selects the Prometheus/VictoriaMetrics data source to bind the dashboard to.
4. System imports the dashboard and displays it with panels populated from live metrics data.
5. Operator sees the following dashboard sections:
   - **HTTP Overview:** Request rate (req/s), error rate (% 5xx), p50/p95/p99 latency across all endpoints.
   - **Top Endpoints:** Table of the top 10 endpoints by request count, with average latency and error count.
   - **JVM Health:** Heap memory usage over time, GC pause frequency and duration, thread count by state.
   - **Database Pool:** Active, idle, and pending connection counts over time relative to pool maximum.
   - **Business Metrics:** Total bookmarks, total collections, shared collections — as gauges with trend sparklines.
   - **Scheduled Jobs:** Job execution count, success/failure ratio, and average duration per job.
6. Operator can set the time range (e.g., last 1 hour, last 24 hours, last 7 days) and the dashboard refreshes automatically.

## Main Success Scenario — Operator Creates Custom Dashboard

1. Operator opens the Grafana UI and creates a new dashboard.
2. Operator adds a new panel and selects the Prometheus/VictoriaMetrics data source.
3. Operator writes a PromQL query using the Chainlink metric names documented in UC-088 (e.g., `rate(http.server.requests{uri=~"/api/.*"}[5m])`).
4. Operator configures the panel visualization (time series, stat, gauge, table).
5. Operator saves the dashboard.
6. Dashboard displays live data from the Chainlink metrics endpoint.

## Alternative Flows

### A1: Dashboard Shows "No Data"

**Trigger:** The dashboard panels display "No data" after import (step 4 of import flow).
**Flow:**

1. Operator verifies that the metrics scraper is running and successfully scraping the Chainlink endpoint (UC-089).
2. Operator checks the scraper's targets page (e.g., Prometheus `/targets`) to confirm the Chainlink target is `UP`.
3. Operator verifies that the data source in Grafana is correctly configured (URL matches the scraper's query endpoint).
4. If the target is `DOWN`, operator checks network connectivity between scraper and Chainlink.
5. Once the scraper is healthy, data begins flowing and the dashboard populates.

### A2: Operator Uses a Non-Grafana Dashboard

**Trigger:** The operator prefers a different visualization tool (e.g., Chronograf, Netdata UI, InfluxDB UI).
**Flow:**

1. The provided dashboard JSON (Grafana-specific) is not directly importable.
2. Operator uses the metric names documented in UC-088 to build equivalent panels in their preferred tool.
3. The PromQL queries from the provided dashboard JSON serve as a reference for the metric names and label selectors to use.

### A3: Operator Configures Alerts

**Trigger:** Operator wants to receive notifications when metrics cross a threshold.
**Flow:**

1. Operator defines alert rules in the dashboarding tool (e.g., Grafana alerting) or in the scraper's alert manager (e.g., Prometheus Alertmanager).
2. Operator configures notification channels (email, Slack, webhook).
3. Example alert rules:
   - `http.server.requests` 5xx rate > 5% for 5 minutes → "High error rate"
   - `jdbc.connections.pending` > 5 for 2 minutes → "Database pool exhaustion"
   - `jvm.memory.used` (heap) > 90% of max for 5 minutes → "JVM memory pressure"
4. Alerts fire independently of dashboard viewing.

### A4: Multiple Chainlink Instances

**Trigger:** The operator runs multiple Chainlink instances behind a load balancer, each exposing its own metrics endpoint.
**Flow:**

1. Operator configures the scraper to discover and scrape each instance separately (using `instance` labels).
2. The provided dashboard uses the `instance` label to allow filtering by instance.
3. Operator can view aggregate metrics across all instances or drill into a specific instance.

## Postconditions

### Success Postconditions

- A Grafana dashboard (imported or custom) displays live Chainlink metrics.
- The operator can monitor HTTP performance, JVM health, database pool, business counters, and job execution.
- The operator can set time ranges and refresh intervals.
- The dashboard configuration JSON is versioned in the project repository.

### Failure Postconditions

- The dashboard shows no data or partial data.
- The operator must troubleshoot the scraper or data source configuration.
- Chainlink application functionality is unaffected — dashboards are non-critical visualization tooling.

## Business Rules

### BR-001: Dashboard JSON Is a Reference Artifact

The provided Grafana dashboard JSON file (`docs/monitoring/grafana-chainlink-dashboard.json`) is a reference artifact that operators can import as-is or customize. It is not required infrastructure — operators can build their own dashboards using the documented metric names.

### BR-002: Dashboard Does Not Require Chainlink Code Changes

All dashboard configuration lives outside the Chainlink application. The dashboard queries the metrics storage backend, not the Chainlink application directly. Changing, adding, or removing dashboard panels never requires redeploying Chainlink.

### BR-003: Example Docker Compose Is Optional

The project may provide a `docker-compose.monitoring.yml` file that starts VictoriaMetrics + Grafana with the Chainlink dashboard pre-loaded. This file is reference material for quick-start evaluation, not a required component of the Chainlink deployment.

---

## Provided Artifacts

The following files are provided in the project repository as reference monitoring infrastructure:

| File | Purpose |
|---|---|
| `docs/monitoring/grafana-chainlink-dashboard.json` | Grafana dashboard model with panels for HTTP, JVM, DB pool, business metrics, and jobs |
| `docs/monitoring/docker-compose.monitoring.yml` | Optional Docker Compose stack: VictoriaMetrics (single-node) + Grafana with pre-loaded dashboard |
| `docs/monitoring/prometheus.yml` | Example scraper configuration for VictoriaMetrics vmagent or Prometheus |
| `docs/monitoring/README.md` | Setup instructions for the monitoring stack |

---

## Reference

### Related Requirements

| ID | Title | Relationship |
|---|---|---|
| FR-090 | Metrics Dashboard | This use case implements FR-090 |
| FR-088 | Application Metrics Collection | Dashboard visualizes these metrics |
| FR-089 | Metrics Export Endpoint | Dashboard consumes data scraped from this endpoint |
| C-022 | Observability Stack is Pluggable | Dashboard tool choice is operator's decision |

### Related Use Cases

| ID | Title | Relationship |
|---|---|---|
| UC-088 | Collect Application Metrics | Dashboard displays the metrics collected there |
| UC-089 | Export Metrics to Observability Stack | Dashboard queries the backend that scrapes this endpoint |
