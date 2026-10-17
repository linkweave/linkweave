# Use Case: Survive SQLite Lock Contention Without Failing User Requests

## Overview

**Use Case ID:** UC-109
**Use Case Name:** Survive SQLite Lock Contention Without Failing User Requests
**Primary Actor:** User (saving a bookmark)
**Secondary Actors:** Scheduler (background jobs), Operator, Agroal connection pool
**Goal:** Make lock contention on the single SQLite file a bounded, observable, self-clearing condition rather than a mystery: saves stay within their latency budget or fail fast with a retryable answer, background jobs step aside instead of hammering, and the operator can see contention in a metric instead of inferring it from a wall of stack traces.
**Status:** Draft

## Traceability

**Maps to:** NFR-030
**Related:** UC-095 / NFR-028 (WAL feasibility — the investigation that produced today's configuration); NFR-001 / NFR-002 (latency budgets); C-003 (single SQLite database); UC-054 / UC-055 (screenshot capture and cleanup jobs — the background writers); UC-051 (favicon cache cleanup); UC-108 (the same incident's other half — host load from repeated model pulls); UC-030 (Robust Error Handling)

---

## Background — observed failure (2026-08-28)

During a session where saving bookmarks was intermittently very slow, the log filled with:

```
ARJUNA016039: onePhaseCommit ... failed with exception ...
  Error trying to transactionCommit local transaction:
  [SQLITE_BUSY] The database file is locked (database is locked)
  ... SqliteBusyRetryInterceptor.retryOnBusy

Error occurred while executing task for trigger IntervalTrigger [id=screenshot-capture, interval=30000]
  jakarta.persistence.PessimisticLockException: JDBC exception executing SQL
  [[SQLITE_BUSY] ...] [select b1_0.id,b1_0.url, ... from Bookmark b1_0 join Collection c1_0 ...]

unhandled generic exception: JDBC exception executing SQL [[SQLITE_BUSY] ...]
  [select ... from User u1_0 where u1_0.email=?]
  PessimisticLockException authenticate
  org.linkweave.api.shared.user.User__JpaTrustedIdentityProviderImpl in authenticate
```

Three things stand out, and each is a rule below:

1. **Contention reached the user as a hard failure.** The last trace is the *authentication* lookup — the query that runs in front of every request. It surfaced as an "unhandled generic exception", i.e. an HTTP 500 with a correlation id, not as a retry and not as a retryable status. Contention on a self-hosted single-file database is an expected transient condition; it must not present as an application crash.
2. **The stall budget multiplies, and it is invisible.** The JDBC URL sets `busy_timeout=10000` (`api/src/main/resources/application.properties:55`) — that is ten seconds the *driver* will wait on each attempt. On top of that, `BookmarkResource` is annotated `@RetryOnSqliteBusy(attempts = 12)`, and the interceptor re-runs the whole transaction each time. The two settings compose multiplicatively: a contended bookmark save can wait up to **twelve times ten seconds**, plus ~1.4 s of backoff, before it either succeeds or fails — against NFR-002's 500 ms budget. Neither setting is wrong on its own; nobody multiplied them. And nothing in the logs distinguishes "waited 40 s and succeeded" from "was fast", because only outright failures are recorded.
3. **The background job kept its cadence regardless.** `screenshot-capture` runs every 30 s (`ScreenshotCaptureJobService`) and re-entered a database that was already saturated, failing with a full stack trace each time. Under contention a batch job should yield to interactive work, not compete with it on a fixed timer.

The load-test gate from UC-095 (`SqliteWriteContentionLoadITest`, strict mode) passes — it models concurrent interactive writers, but not this incident's shape: scheduled jobs plus interactive writes plus a host whose CPU and disk are busy with something else (in this incident, repeated model pulls — UC-108).

## Preconditions

- The application runs against a single SQLite file in WAL mode (C-003, current configuration).
- Background jobs are enabled (screenshot capture, cache cleanups, trashbin retention).

## Main Success Scenario

1. User saves a bookmark while a background job is writing.
2. The write encounters `SQLITE_BUSY`.
3. System retries the whole transaction, with capped exponential backoff and jitter, from outside the transaction boundary (existing `@RetryOnSqliteBusy` mechanism).
4. A retry succeeds within the request's contention budget (BR-109-4).
5. System records the contention event in a counter (BR-109-6); the log stays quiet because nothing failed.
6. User sees a normal, successful save.

## Alternative Flows

### A1: Retries Exhausted

**Trigger:** Every attempt within the budget hits `SQLITE_BUSY` (step 4).
**Flow:**

1. System answers with a **retryable** response — HTTP 503 with `Retry-After` — not a generic 500 (BR-109-1).
2. The client keeps the user's unsaved input, shows a "busy, retrying…" state, and retries once automatically before asking the user to retry (BR-109-9).
3. System logs one warning with the operation name and the attempt count — one line, not a stack trace per attempt.

### A2: Contention on a Read

**Trigger:** A read (authentication lookup, list query) fails with `SQLITE_BUSY` (step 2).
**Flow:**

1. The read is covered by the same retry boundary as writes (BR-109-3): reads are not exempt, because the authentication read gates every request.
2. Use case continues at step 4.

### A3: Background Job Meets Contention

**Trigger:** A scheduled job's query or write fails with `SQLITE_BUSY` (step 2).
**Flow:**

1. The job abandons the current tick immediately — no retry storm — and increases its own backoff, skipping ticks until it succeeds again (BR-109-5).
2. It logs one warning with the root-cause message, not a stack trace, per backoff transition.
3. Deferred work is picked up on a later tick; nothing is lost, because the job's work set is derived from the database, not from a queue.

### A4: Sustained Contention

**Trigger:** Contention persists beyond a configured window (many events across several minutes).
**Flow:**

1. System surfaces sustained contention as a distinct, alertable signal (metric plus one WARN), so an operator can act rather than watching request latency drift.
2. Operator consults the runbook (BR-109-7) — most commonly an external SQL client holding a transaction on the database file, or a host that is out of I/O.

### A5: A Second Writer Process

**Trigger:** Another process (IDE database tool, `sqlite3` shell, a second application instance, a backup that copies mid-write) holds a write transaction on the file.
**Flow:**

1. Requests stall for the full `busy_timeout` for as long as that transaction is open — the application cannot fix this from inside.
2. System's contention metric and log make the pattern recognizable (contention with no corresponding internal write activity).
3. Documentation states the single-writer rule plainly (BR-109-7).

## Postconditions

### Success Postconditions

- No user request fails with an unhandled 500 because of lock contention.
- The time any user request can spend waiting on a lock is bounded and known.
- Background jobs reduce their own load when the database is busy.
- Contention is visible as a metric before it is visible as a complaint.

### Failure Postconditions

- The user gets an explicit, retryable "the database is busy" answer with their input preserved, and one log line the operator can search for.

## Business Rules

### BR-109-1: Contention Is Never an Unhandled Error

`SQLITE_BUSY`, at any layer (query, flush, or transaction commit), must be classified as a transient condition. After retries are exhausted it maps to HTTP 503 with `Retry-After`, and the client renders a "busy, try again" message. It must never reach the generic exception handler as an unhandled 500, and a stack trace for it must never be shown to a user.

### BR-109-2: Every Interactive Entry Point Has a Retry Boundary

The retry boundary sits at the outermost transaction boundary of each interactive operation — which is where `SqliteBusyRetryInterceptor` can actually re-run the work (it logs a warning and gives up when a caller's transaction is already active). Coverage is enforced, not remembered: an automated architecture test fails the build when a transactional resource method has no retry boundary above it.

### BR-109-3: The Authentication Path Is Covered Too

Identity lookup runs in front of every request, so contention there fails everything at once. It is subject to the same retry boundary and the same classification as any other operation; being framework-owned code is not an exemption.

### BR-109-4: The Wait Is Bounded and Budgeted

The total time a user request may spend waiting on locks is one explicit, configured budget aligned with NFR-002 — not an accident of two independent settings that multiply. The driver's `busy_timeout` bounds a *single* attempt; the retry policy bounds the *number* of attempts; their product is what the user experiences, and that product is what must be configured and asserted. Today's combination (10 s × 12 attempts on the bookmark save path, plus backoff) allows a wait of well over a minute. The driver timeout must be reduced so that application-level retry with jitter does the waiting, and the product must be bounded to a few seconds at most, after which A1 applies.

Time is not the only thing the attempt count multiplies. `@RetryOnSqliteBusy` sits outside the SmallRye Fault Tolerance interceptor, so every attempt spends another `@RateLimit` permit from a process-wide bucket — and reordering is not available, because moving inside fault tolerance would also move inside the transaction. `/auth/register` is the acute case: 5 permits per minute, so at the class default of 5 attempts one contended registration would close registration for the rest of the minute. `JaxResourceTest.retry_attempts_must_not_be_able_to_drain_a_rate_limit_bucket` holds a single call to half its bucket; the process-wide budget is still open.

### BR-109-5: Background Jobs Yield to Interactive Work

A scheduled job that encounters contention abandons its tick and backs off exponentially (skipping ticks up to a ceiling) before trying again. It never retries within a tick, never runs at a fixed cadence into a contended database, and logs one warning per backoff transition, with the root-cause message only. Job cadences and batch sizes are configuration, so an operator can slow a job down without a rebuild.

### BR-109-6: Contention Is Measurable Before It Is Painful

A counter records lock-contention events, tagged by operation class (`interactive-read`, `interactive-write`, `job`) and outcome (`recovered` / `exhausted`), within the cardinality budget (NFR-024). A timer records lock wait time. Successful-but-slow requests are therefore visible — the current logs only prove that something failed, never that a save waited nine seconds.

### BR-109-7: One Writing Process, Documented

Exactly one process writes the database file. The deployment and developer documentation must state that attaching an external SQL client (IDE datasource, `sqlite3` shell) with an open transaction, running a second application instance against the same file, or copying the file mid-write will stall or fail the application — and how to recognize that from the contention metric. This is a documented operating constraint of C-003, not a bug to be engineered around.

### BR-109-8: The Regression Gate Models the Real Workload

The contention load test (`SqliteWriteContentionLoadITest`, the gate established by UC-095) must include the shape that actually failed: scheduled background jobs running concurrently with interactive writes and reads. It asserts zero non-2xx responses and a bounded p99 for interactive operations. Any change to pool sizing, `busy_timeout`, journal mode, job cadence, or the retry policy must be justified against that gate.

### BR-109-9: The User's Work Is Never Lost to Contention

A save that fails or is retried because of contention preserves the user's form input, retries once automatically, and only then asks the user to act. A transient database condition must not cost the user their typing.

The automatic retry must be a single attempt and must honour `Retry-After`: server-side retry already spends a permit per attempt (BR-109-4), and on the 5-permit endpoints a client that retries eagerly drains the bucket.

## Acceptance Criteria

1. Under the extended load test (BR-109-8) — interactive writes plus running background jobs — every interactive request returns 2xx and the p99 stays within the configured contention budget.
2. With writes artificially blocked past the budget, an interactive save returns 503 with `Retry-After` and the client shows the retry state with the form still filled; no 500 and no stack trace reaches the user.
3. With writes artificially blocked, the screenshot-capture job skips ticks with growing intervals and produces one warning per transition, not one stack trace per tick.
4. Contention counters and the wait timer appear on the metrics endpoint and move under the load test.
5. The architecture test fails when a transactional resource method is added without a retry boundary.

## Notes / Future Considerations

- This use case does not revisit the engine choice — UC-095 settled that (WAL, single writer, no engine change). It hardens the behaviour *around* the ceiling that WAL leaves in place: writers still serialize, so contention is a normal operating state and must be treated as one.
- Host saturation is a contention multiplier: in this incident, repeated model pulls (UC-108) were competing for the same CPU and disk. Fixing either half alone leaves the other able to reproduce the symptom.
