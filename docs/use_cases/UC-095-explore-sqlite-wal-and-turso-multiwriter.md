# Use Case: Explore SQLite WAL Mode and Evaluate Turso Database (Limbo) for Multi-Writer Concurrency

## Overview

**Use Case ID:** UC-095
**Use Case Name:** Explore SQLite WAL Mode and Evaluate Turso Database (Limbo) for Multi-Writer Concurrency
**Primary Actor:** Developer / Architect
**Secondary Actors:** Agroal connection pool (quarkus-datasource), CI load-test harness
**Goal:** Determine how to make LinkWeave's single-file SQLite database (C-003) scale to concurrent write workloads (interactive requests *plus* background jobs running simultaneously) without `SQLITE_BUSY` stalls, and produce a documented, recommendation-backed decision on (a) enabling WAL mode, (b) adopting Turso Database (Limbo, the Rust SQLite rewrite whose MVCC/`BEGIN CONCURRENT` is the one SQLite-family path to true concurrent writers), or (c) an application-level write-serialization strategy.
**Status:** Open

## Traceability

**Maps to:** NFR-028 (SQLite Concurrency via WAL Mode — investigation-spurring placeholder), NFR-001/NFR-002 (page load / API response time under concurrent load), C-003 (Single Database), C-004 (self-hostable, no external cloud deps)
**Related:** UC-061 (Automated Backup — WAL sidecar files affect backup procedure), favicon/screenshot cleanup jobs (UC-051/UC-055), batch operations (UC-073/UC-074)

---

## Background

LinkWeave stores all tenant data in a single SQLite file (C-003) accessed through the Agroal JDBC pool via the `io.quarkiverse.jdbc:quarkus-jdbc-sqlite` extension (which wraps the Xerial `sqlite-jdbc` driver and adds Quarkus-native integration: dev services, health checks, native-image support, config). The JDBC URL currently sets `foreign_keys=on&busy_timeout=10000` but runs in the default **rollback-journal** mode, and **no explicit Agroal `max-size` is configured — so the pool defaults to ~20 connections all contending on one file** (`api/src/main/resources/application.properties:46`). Write contention is therefore a real, growing risk because several write paths can fire concurrently:

- Interactive user writes (create/edit/delete bookmark, drag-and-drop reorder, batch tag/move/delete).
- Scheduled background jobs (favicon cache cleanup UC-051, screenshot cache cleanup UC-055, trashbin retention).
- Metrics/business-counter updates and Flyway-adjacent housekeeping.

SQLite's concurrency model has a hard ceiling that is the crux of this use case:

- **WAL mode** allows *many concurrent readers alongside a single writer*. It does **not** permit multiple concurrent writers — the database still takes one write lock at a time (RESERVED → PENDING → EXCLUSIVE on the whole DB). WAL therefore helps mixed read/write workloads (readers no longer block the writer and vice-versa) but does **not** raise the write ceiling.
- **Rollback-journal mode** (today) additionally makes readers block behind a writer, compounding contention.

So for the *multiple-writers* emphasis of this use case, WAL alone is necessary-but-insufficient, and the evaluation must also cover Turso Database (Limbo) and application-level write serialization. Note the Turso naming, which is easy to conflate: **libSQL** is Turso's production-ready *fork* of SQLite (primary-replica, writes still serialize at the primary) and is **not** evaluated here; **Turso Database (codenamed Limbo)** is a separate clean-room *rewrite* of SQLite in Rust that adds MVCC + `BEGIN CONCURRENT` and is the only SQLite-compatible option targeting genuine concurrent writers — it is the candidate this use case assesses.

### Candidates under evaluation

1. **SQLite WAL mode (stay on SQLite).** Enable `journal_mode=WAL` via the JDBC URL alongside the existing `busy_timeout`/`foreign_keys` pragmas (WAL is persisted in the DB header, so the URL pragma is idempotent — see Implementation Notes for why this is preferred over a Flyway migration here), tune `synchronous`/`wal_checkpoint`. Cheapest change; addresses read concurrency; writers still serialized.
2. **Connection-pool sizing + app-level write serialization.** The simplest and lowest-code form of write serialization is **Agroal pool sizing**: setting `quarkus.datasource.jdbc.max-size=1` serializes *all* DB access by construction so the pool never sees two concurrent write transactions, eliminating `SQLITE_BUSY` with zero application code (at the cost of also serializing reads — which WAL otherwise parallelizes). A bounded small pool (e.g. `max-size` 4–8) + WAL is the realistic middle ground. Only if pool tuning proves insufficient should a dedicated writer thread/executor or write queue be built. Pairs with #1. Keeps SQLite; removes writer contention without an engine change.
3. **Turso Database (Limbo).** Turso Database (codenamed Limbo) is a from-scratch rewrite of SQLite in Rust — *not* the libSQL fork — that keeps SQLite's file format and SQL dialect while adding fully asynchronous I/O (`io_uring` on Linux) and, crucially, **MVCC with `BEGIN CONCURRENT`: multiple transactions can write simultaneously without blocking each other or readers.** This is the only SQLite-compatible candidate that actually raises the write ceiling (unlike WAL or libSQL, which serialize writers), so it directly addresses this use case's multiple-writers goal. **However, it is not adoptable today:**
   - **Maturity:** in beta / "early technology preview." libSQL is production-ready; Turso Database is explicitly not yet. Adoption now would be a bet on an evolving engine.
   - **MVCC limitations:** indexes cannot currently be created on MVCC-enabled databases, and the entire dataset is eagerly loaded into memory on first access — a poor fit for a growing, index-heavy bookmark DB.
   - **No JDBC/Java driver:** it is an in-process engine with Rust/WASM/JS bindings; there is no JDBC datasource for Quarkus + Hibernate to bind to. This is a harder blocker than libSQL's driver-maturity question — there is currently no integration path for this stack at all.
   - **C-004:** the embedded/self-hosted engine is fine for C-004; only the managed Turso Cloud (where the concurrent-write feature is also gated behind a waitlist) introduces external-cloud dependencies and would conflict with C-004.

   Net: track it as the future answer to true multi-writer on a single SQLite-format file, but it is a *watch-and-wait* item — re-evaluate when it reaches production maturity and a JDBC/Hibernate path exists.
4. **PostgreSQL (heavy option, out of scope unless justified).** True row-level MVCC multi-writer concurrency, but it breaks the C-003 single-file, zero-ops ethos. Carried only as the reference point for what "real" multi-writer would cost; C-003 already notes multi-database architecture may be considered for future scaling.

## Preconditions

- A reproducible concurrency/load test exists (or is built as the first step) that drives simultaneous interactive writes and background jobs against the current rollback-journal configuration, to establish a `SQLITE_BUSY` / latency baseline.
- The Agroal pool sizing and current `busy_timeout` are understood. Note: no `max-size` is set today, so the pool defaults to ~20 connections — this is the primary contention lever and must be part of the baseline.
- A running Quarkus dev instance with the existing `linkweave.db` for hands-on WAL validation.

## Main Success Scenario

1. Developer builds a repeatable multi-writer load test (concurrent interactive writes + background cleanup jobs + batch operations) and records the baseline: `SQLITE_BUSY` rate, p99 write latency, and reader stalls under rollback-journal mode.
2. Developer enables WAL by appending `journal_mode=WAL` to the JDBC URL (WAL persists in the DB header, so this is idempotent across connections; **avoid a Flyway migration for this** — Flyway runs each migration inside a transaction and SQLite cannot change `journal_mode` within a transaction) and re-runs the same test. Developer also sets an explicit `quarkus.datasource.jdbc.max-size` and treats it as a primary tuning variable.
3. Developer confirms WAL's expected behaviour: readers no longer block on the writer and the writer no longer blocks readers; `SQLITE_BUSY` is reduced for mixed read/write loads.
4. Developer confirms the remaining *multiple-writer* gap: concurrent write transactions still serialize, and under heavy write parallelism `SQLITE_BUSY`/timeouts persist beyond what `busy_timeout` smoothing alone can hide.
5. Developer evaluates connection-pool sizing + application-level write serialization (Candidate #2) **first**, as the lowest-risk option: benchmarks a bounded pool (and, at the extreme, `max-size=1`) with WAL, and only if pool tuning is insufficient prototypes a single-writer executor / write queue. This stays on raw SQLite + the existing Quarkus extension.
6. **Only if step 4/5 shows the write ceiling is still breached within NFR-001/NFR-002 budgets**, Developer assesses Turso Database (Limbo) as the path to true concurrent writers. Because there is no JDBC/Hibernate driver today, this is a **paper/feasibility assessment, not a code spike**: confirm current maturity (beta/preview), the MVCC limitations (no indexes on MVCC DBs, eager in-memory load), and the absence of a Quarkus/JDBC integration path; record what production-readiness signals and driver availability would have to change before a real spike is worth running. The assessment is **gated** on this trigger — it is not run unconditionally.
7. Developer captures durability/crash-recovery, checkpoint strategy, synchronous-mode, backup, and NFS caveats for the recommended option.
8. Developer writes the feasibility report (`docs/sqlite-wal-feasibility.md`) with a recommendation and, if positive, an implementation plan.
9. Report is reviewed; the chosen option is implemented (or explicitly deferred with rationale); NFR-028 is converted into a concrete implementation NFR or closed.

## Alternative Flows

### A1: WAL Is Sufficient for the Expected Load

**Trigger:** Step 4 shows that, at realistically expected concurrency, WAL + a tuned `busy_timeout` keeps `SQLITE_BUSY` and latency within NFR-001/NFR-002 budgets.
**Flow:**

1. Developer documents WAL as the recommendation and closes the Turso Database (Limbo) and write-serialization investigations as "not needed at current scale."
2. Developer implements WAL (migration + checkpoint/synchronous tuning) and updates the backup procedure for `-wal`/`.shm` sidecar files.
3. Use case ends at step 9.

### A2: Multiple Writers Must Scale Beyond a Single Serialized Writer

**Trigger:** Step 4/6 shows write serialization is a hard ceiling that the product will outgrow (e.g. many concurrent batch jobs + interactive users), and neither WAL nor an app-level single-writer queue is enough.
**Flow:**

1. Developer documents that the only *production-ready* multi-writer engine is PostgreSQL: SQLite (even with WAL) serializes writers, and the one SQLite-family engine that offers true concurrent writers — Turso Database (Limbo, MVCC/`BEGIN CONCURRENT`) — is still beta and lacks a JDBC path, so it cannot be adopted now.
2. Developer escalates to the PostgreSQL reference option and quantifies the cost against C-003/C-004, while recording Turso Database (Limbo) as the SQLite-format multi-writer option to re-evaluate once it is production-ready and JDBC-accessible.
3. A separate decision/ADR is raised to amend C-003 (multi-database) since this exceeds the scope of this use case.
4. Use case ends at step 9 with the escalation recorded.

### A3: Turso Database (Limbo) Adoption Is Blocked by Maturity / JDBC Integration

**Trigger:** Step 6 confirms Turso Database (Limbo) cannot be adopted now — no JDBC/Hibernate driver exists, the engine is still beta/preview, and/or the MVCC limitations (no indexes on MVCC DBs, eager in-memory load of the dataset) are disqualifying for LinkWeave's data.
**Flow:**

1. Developer records the specific blockers (absence of a JDBC datasource for Quarkus + Hibernate, beta maturity, MVCC index/memory constraints, `BEGIN CONCURRENT` transaction semantics) and the concrete signals that would reopen the question (production GA, a maintained JDBC/Hibernate path, index support on MVCC DBs).
2. Developer falls back to WAL + pool sizing / application-level write serialization (Candidate #1 + #2) and records Turso Database (Limbo) as "re-evaluate when production-ready and JDBC-accessible."
3. Use case ends at step 9.

### A4: Managed Turso Cloud Considered

**Trigger:** The team wants zero-ops hosted operation (including Turso Cloud's gated concurrent-write feature) and is willing to revisit C-004.
**Flow:**

1. Developer flags that managed Turso Cloud conflicts with C-004 (self-hostable, no external cloud dependencies).
2. Decision is escalated as a constraints change; the self-hosted embedded engine is the only C-004-compatible form, and only once it is mature enough (see A3) to adopt at all.
3. Use case continues at step 8 with the constraint tension explicitly documented.

## Postconditions

### Success Postconditions

- A feasibility report exists at `docs/sqlite-wal-feasibility.md` with a clear recommendation covering WAL, multiple-writer behaviour, and the Turso Database (Limbo) verdict.
- Whatever option is chosen is either implemented, or explicitly deferred with a recorded rationale and a load-test baseline.
- NFR-028 is resolved (converted to a concrete implementation NFR, or closed).
- If WAL is adopted, the backup procedure (UC-061) and deployment guide are updated for sidecar files and the NFS caveat.

### Failure Postconditions

- No recommendation is produced; `SQLITE_BUSY` risk under multi-user + background-job load remains unquantified, and NFR-028 stays open.

## Business Rules

### BR-095-1: WAL Does Not Create Multiple Writers

Any recommendation must state explicitly that WAL improves *read* concurrency and reader/writer non-blocking, but that plain SQLite still serializes writes to a single writer. "Enable WAL" must not be presented as a solution to concurrent writes on its own. The only SQLite-family engine that genuinely allows concurrent writers is Turso Database (Limbo) via MVCC/`BEGIN CONCURRENT` — and the recommendation must equally state that this is beta and currently lacks a JDBC path, so it is not an adoptable multi-writer solution today either.

### BR-095-2: Decision Must Be Evidence-Based

The recommendation must be backed by the step-1/step-2 load-test numbers (baseline vs WAL/pool-sizing candidates) against the same multi-writer workload, not by general SQLite/Turso marketing claims. The Turso Database (Limbo) verdict (step 6) is necessarily a maturity/feasibility judgement rather than a benchmark, since no JDBC path exists to benchmark against — this must be stated as such.

### BR-095-3: Honour C-003 and C-004

Adopting managed Turso Cloud is treated as a constraints change (it conflicts with C-004) and must be escalated, not silently introduced. Any move off the single SQLite file (e.g. PostgreSQL) similarly requires revisiting C-003.

### BR-095-4: Self-Hosted Bias

Because LinkWeave is self-hosted and zero-ops (C-004), the default preference order is: WAL (Candidate #1) → WAL + pool sizing / app-level write serialization (#2, starting with `max-size` config before any executor/queue) → Turso Database (Limbo) (#3, *future* option — only once production-ready and JDBC-accessible; the one path to true concurrent writers on a single SQLite-format file) → PostgreSQL (#4, the production-ready multi-writer fallback if multi-writer is needed before Limbo matures). Options requiring external cloud services are deprioritized.

### BR-095-5: Backup and Durability Must Be Re-Specified

If WAL is adopted, the checkpoint strategy (`PASSIVE`/`FULL`/`TRUNCATE`), `synchronous` mode (`NORMAL`/`FULL`), and the requirement that backups capture `.db` + `-wal` + `.shm` together must be documented, and the NFS/network-volume caveat must be added to the deployment guide.

---

## Evaluation Criteria / Decision Matrix

| Criterion                                     | WAL (stay on SQLite) | WAL + pool sizing (`max-size`) | WAL + single-writer queue | Turso Database (Limbo) | PostgreSQL |
|-----------------------------------------------|:--------------------:|:------------------------------:|:-------------------------:|:----------------------:|:----------:|
| Read concurrency improvement                  | High                 | Medium (capped pool)           | High                      | High                   | High       |
| Multiple concurrent writers                   | No (serialized)      | No (by design)                 | No (by design)            | **Yes** (MVCC/`BEGIN CONCURRENT`) | **Yes** |
| Mitigates `SQLITE_BUSY` under mixed load      | High                 | High (eliminated at `max-size=1`) | High                   | High                   | High       |
| Implementation effort / risk                  | Low                  | **Lowest (config only)**       | Medium                    | **Blocked today** (beta; no JDBC driver; MVCC: no indexes, eager in-memory load) | High |
| Keeps C-003 (single file)                     | Yes                  | Yes                            | Yes                       | Yes (SQLite file format) | No       |
| Keeps C-004 (self-hosted, no cloud)           | Yes                  | Yes                            | Yes                       | Yes (embedded; not Turso Cloud) | Yes  |
| Operational complexity                        | Minimal              | Minimal                        | Low                       | Unknown (immature)     | High       |

> **Reading the Limbo column:** it is the only SQLite-format option that checks "multiple concurrent writers," but "Blocked today" in the effort row is decisive — there is no JDBC path for Quarkus/Hibernate and the engine is beta. It is a *future* candidate, not a current one.

## Implementation Notes

- **Baseline first.** Do not choose an option without the step-1 load test; the whole use case hinges on whether realistic concurrency actually hits the write ceiling. Given LinkWeave is a self-hosted, zero-ops app with low expected concurrency, the realistic outcome is that WAL + pool sizing + `busy_timeout` (Candidates #1/#2) suffices, and the Turso Database (Limbo) assessment (step 6) is never triggered.
- **Don't conflate Turso's two projects.** *libSQL* is the production-ready SQLite fork (primary-replica, writes serialize) and is **not** what this use case targets. *Turso Database (Limbo)* is the Rust rewrite with MVCC/`BEGIN CONCURRENT` — the actual multi-writer candidate — but it is beta and has no JDBC driver, so step 6 is a feasibility judgement, not a spike.
- **WAL via JDBC URL, not a Flyway migration.** Append `journal_mode=WAL` to the existing JDBC URL (next to `foreign_keys=on&busy_timeout=10000`). WAL persists in the DB header so this is idempotent. **Do not use a Flyway migration for it:** Flyway wraps each migration in a transaction and SQLite refuses to change `journal_mode` inside a transaction.
- **Pool sizing is the primary write-serialization lever.** No `max-size` is set today (Agroal defaults to ~20). Setting `quarkus.datasource.jdbc.max-size=1` removes `SQLITE_BUSY` entirely (serializes everything); a bounded small pool + WAL is the middle ground that keeps read parallelism. Tune this before building any executor/queue.
- **`busy_timeout` tuning.** The current 10000 ms is a long stall; pair WAL with a shorter, bounded timeout plus app-level retry (and/or pool sizing) rather than relying on a 10 s block.
- **Deliverable.** The feasibility report (`docs/sqlite-wal-feasibility.md`) named in NFR-028 is the artifact this use case produces; it should contain the baseline numbers, the matrix above filled in with real data, and a single recommendation.
