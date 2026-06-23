# Use Case: Collect Application Metrics

## Overview

**Use Case ID:** UC-088
**Use Case Name:** Collect Application Metrics
**Primary Actor:** System (automatic — no actor interaction required)
**Goal:** Continuously collect application-level metrics (HTTP request rates and latencies, JVM health, database pool usage, scheduled job execution, and custom business counters) so that operators can monitor system health and diagnose issues.
**Status:** Draft

## Traceability

**Maps to:** FR-088, NFR-024, NFR-025, NFR-026, C-021

---

## Preconditions

- The Quarkus application is running with the `quarkus-micrometer` and `quarkus-micrometer-registry-prometheus` extensions on the classpath.
- The Micrometer `MeterRegistry` is available via CDI injection.

## Main Success Scenario — Automatic Infrastructure Metrics

1. System starts the Quarkus application.
2. System auto-registers Micrometer meters for Quarkus-managed concerns: HTTP server request counts and latencies per endpoint path template, HTTP response status code classes, and Vert.x event-loop utilization.
3. System auto-registers JVM meters: heap and non-heap memory usage, garbage collection counts and durations, thread counts by state.
4. System auto-registers database connection pool meters: active connections, idle connections, pending acquisition count, and acquisition time.
5. System auto-registers scheduled job meters: execution count, success/failure count, and execution duration per job name.
6. All registered meters are available for scraping via the metrics export endpoint (UC-089).

## Main Success Scenario — Custom Business Metrics

1. System starts the Quarkus application.
2. System registers a `MeterRegistry` bean that custom service classes can inject.
3. `BookmarkService` registers a `Gauge` named `linkweave.bookmarks.total` tagged with `collection_id` that reports the total number of bookmarks in each collection.
4. `CollectionService` registers a `Gauge` named `linkweave.collections.total` that reports the total number of collections.
5. `CollectionAccessService` registers a `Gauge` named `linkweave.collections.shared` that reports the number of collections with at least one access grant.
6. System validates that no metric tag uses high-cardinality values (BR-001).
7. All custom meters are available for scraping alongside infrastructure meters via the metrics export endpoint (UC-089).

## Alternative Flows

### A1: Gauge Reports Zero for Empty Collection

**Trigger:** A collection has no bookmarks (step 3 of custom metrics).
**Flow:**

1. The `linkweave.bookmarks.total` gauge reports `0` for that `collection_id`.
2. The metric is still emitted (zero is a valid value, not an absence of data).

### A2: Collection Deleted After Gauge Registration

**Trigger:** A collection is deleted after its bookmark-count gauge was registered (step 3 of custom metrics).
**Flow:**

1. On the next gauge evaluation, the collection no longer exists.
2. System removes the gauge for that `collection_id` from the registry.
3. The metric stops being emitted. No stale data remains.

### A3: Meter Registration Fails

**Trigger:** A custom service attempts to register a meter with an invalid name or conflicting type (e.g., registering a `Counter` with a name already used by a `Gauge`).
**Flow:**

1. Micrometer logs a warning at startup.
2. The conflicting meter is skipped.
3. All other meters continue to function normally.
4. Application startup is not blocked.

### A4: Metrics Extension Not on Classpath

**Trigger:** The `quarkus-micrometer` extension is not included as a dependency.
**Flow:**

1. Quarkus starts normally without any metrics infrastructure.
2. The `/q/metrics` endpoint is not registered.
3. Custom service classes that inject `MeterRegistry` receive a no-op `CompositeMeterRegistry` that silently discards all meter registrations (Micrometer's default behavior).
4. No performance overhead is incurred.

## Postconditions

### Success Postconditions

- Infrastructure meters (HTTP, JVM, database pool, scheduled jobs) are registered and emitting values.
- Custom business meters (bookmark counts, collection counts, shared collection counts) are registered and emitting values.
- All meters respect the cardinality budget (BR-001).
- Meters are accessible via the `/q/metrics` endpoint (UC-089).

### Failure Postconditions

- No meters are registered, or only a subset is registered.
- A warning is logged indicating which meter registration failed.
- Application functionality is unaffected — metrics are non-critical infrastructure.

## Business Rules

### BR-001: Cardinality Budget

The total number of unique metric time series (tag combinations) must not exceed 500 per application instance. High-cardinality dimensions (bookmark IDs, user emails, folder names, tag names) must never be used as metric tags. Only the following low-cardinality dimensions are permitted:

- HTTP method (`GET`, `POST`, `PUT`, `DELETE`)
- HTTP status code class (`2xx`, `4xx`, `5xx`)
- Endpoint path template (e.g., `/api/collections/{id}`, not the actual UUID)
- Job name (e.g., `favicon-cleanup`, `screenshot-cleanup`)
- Result status (`success`, `failure`)

Exceeding 500 time series must log a warning at application startup with the current count.

### BR-002: No Metrics in Critical Path

Metric recording operations (incrementing counters, recording timer values, observing gauges) must not throw exceptions that propagate to the caller. All Micrometer operations are inherently safe (they swallow exceptions internally), but custom meter-registration code must also guard against exceptions to prevent metrics from affecting business logic.

### BR-003: Metric Naming Convention

All custom metrics must use the `linkweave.` prefix and follow a dot-separated hierarchical naming scheme: `linkweave.<domain>.<metric-name>`. Examples:

- `linkweave.bookmarks.total`
- `linkweave.collections.total`
- `linkweave.collections.shared`
- `linkweave.jobs.favicon.cleanup.duration`

Infrastructure metrics provided by Quarkus/Micrometer retain their default names (`http.server.requests`, `jvm.memory.used`, etc.).

---

## Metrics Catalog

### Infrastructure Metrics (Auto-Registered by Quarkus/Micrometer)

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `http.server.requests` | Timer | `method`, `uri`, `status` | HTTP request count, total time, and percentile latencies per endpoint |
| `jvm.memory.used` | Gauge | `area` (heap/non-heap), `id` (pool name) | JVM memory usage per memory pool |
| `jvm.gc.pause` | Timer | `cause`, `action` | GC event count and duration |
| `jvm.threads.live` | Gauge | `state` | Thread count by state |
| `jdbc.connections.active` | Gauge | — | Number of active JDBC connections |
| `jdbc.connections.idle` | Gauge | — | Number of idle JDBC connections |
| `jdbc.connections.pending` | Gauge | — | Number of threads waiting for a connection |
| `jdbc.connections.max` | Gauge | — | Maximum configured pool size |

### Custom Business Metrics (Registered by LinkWeave Services)

| Metric Name | Type | Tags | Source | Description |
|---|---|---|---|---|
| `linkweave.bookmarks.total` | Gauge | `collection_id` | `BookmarkService` | Total bookmarks per collection |
| `linkweave.collections.total` | Gauge | — | `CollectionService` | Total number of collections |
| `linkweave.collections.shared` | Gauge | — | `CollectionAccessService` | Number of collections with ≥1 access grant |

### Scheduled Job Metrics (Auto-Registered by Quarkus Scheduler)

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `quarkus.scheduler.job.duration` | Timer | `job` (job name), `result` (success/failure) | Execution count and duration per scheduled job |

> **Note:** The job metrics are automatically registered by the Quarkus scheduler when `quarkus-micrometer` is on the classpath. No custom code is required for favicon cleanup (FR-064) and screenshot cleanup (FR-066) job metrics.

---

## Configuration

All metrics behavior is controlled via Quarkus application properties. No code changes are required to enable, disable, or reconfigure metrics.

| Property | Default | Description |
|---|---|---|
| `quarkus.micrometer.enabled` | `true` | Master switch — set to `false` to disable all metrics |
| `quarkus.micrometer.registry-prometheus.enabled` | `true` | Enable the Prometheus-compatible export format |
| `quarkus.micrometer.export.json.enabled` | `false` | Also expose metrics in JSON format alongside Prometheus text |
| `quarkus.micrometer.binder.http-server.enabled` | `true` | Collect HTTP server request metrics |
| `quarkus.micrometer.binder.jvm.enabled` | `true` | Collect JVM memory, GC, and thread metrics |
| `quarkus.datasource.jdbc.metrics.enabled` | `false` | Collect JDBC connection pool metrics (should be set to `true`) |

---

## Reference

### Related Requirements

| ID | Title | Relationship |
|---|---|---|
| FR-088 | Application Metrics Collection | This use case implements FR-088 |
| FR-089 | Metrics Export Endpoint | Depends on meters registered here |
| FR-090 | Metrics Dashboard | Dashboard visualizes these metrics |
| NFR-024 | Metrics Cardinality Budget | Enforced by BR-001 |
| NFR-025 | Metrics Endpoint Response Time | Depends on number of registered meters |
| NFR-026 | Metrics Collection Overhead | Validates overhead is acceptable |
| C-021 | Metrics via Micrometer | Technology constraint |

### Related Use Cases

| ID | Title | Relationship |
|---|---|---|
| UC-089 | Export Metrics to Observability Stack | Scrapes the meters registered by this use case |
| UC-090 | Visualize Metrics in Dashboard | Displays the metrics collected here |
