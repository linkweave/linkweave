# LinkWeave Monitoring Stack

This directory contains reference artifacts for observing a LinkWeave deployment. This is completely optional.

## Quick Start (Local)

Requires Docker and a running LinkWeave dev instance (`cd api && ./mvnw quarkus:dev`).

```bash
cd docs/monitoring
docker compose -f docker-compose.monitoring.yml up -d
```

- **Grafana**: http://localhost:3000 — user `admin`, password `my-secret-pw`
  > ⚠️ **These are default credentials for local development only.** Change `GF_SECURITY_ADMIN_PASSWORD` in `docker-compose.monitoring.yml` before exposing Grafana to any network.
- **VictoriaMetrics**: http://localhost:8428

Import `grafana-provisioning/dashboards/chainlink-overview.json` via Grafana → Dashboards → Import, then select the VictoriaMetrics data source.

## Metrics Endpoint

The LinkWeave API exposes metrics at `GET /q/metrics` in Prometheus exposition format.

```bash
curl -k https://local-chainlink.localhost:8443/q/metrics | grep chainlink
```

## Files

| File | Purpose |
|---|---|
| `prometheus.yml` | Scraper configuration for VictoriaMetrics vmagent or Prometheus |
| `docker-compose.monitoring.yml` | Optional local stack: VictoriaMetrics + Grafana |
| `grafana-provisioning/dashboards/chainlink-overview.json` | Grafana dashboard (HTTP, JVM, DB pool, business metrics, jobs) |

## Available Metrics

See [UC-088](../use_cases/UC-088-collect-application-metrics.md) for the full metrics catalog.

Key custom metrics:

| Metric | Type | Description |
|---|---|---|
| `chainlink_collections_total` | Gauge | Total collections |
| `chainlink_collections_shared` | Gauge | Collections shared with ≥1 other user |
| `chainlink_bookmarks_total{collection_id}` | Gauge | Bookmarks per collection |

## Configuration

Metrics collection is enabled by default. To disable:

```properties
quarkus.micrometer.enabled=false
```

The refresh interval for business metrics defaults to 30 minutes and can be overridden:

```properties
chainlink.metrics.refresh.every=30m
```
