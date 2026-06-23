# Use Case: Explore SQLite WAL Mode and Evaluate Turso (libSQL) for Multi-Writer Concurrency

## Overview

**Use Case ID:** UC-095
**Use Case Name:** Explore SQLite WAL Mode and Evaluate Turso (libSQL) for Multi-Writer Concurrency
**Primary Actor:** Developer / Architect
**Secondary Actors:** Agroal connection pool (quarkus-datasource), CI load-test harness
**Goal:** Determine how to make LinkWeave's single-file SQLite database (C-003) scale to concurrent write workloads (interactive requests *plus* background jobs running simultaneously) without `SQLITE_BUSY` stalls, and produce a documented, recommendation-backed decision on (a) enabling WAL mode, (b) adopting Turso/libSQL, or (c) an application-level write-serialization strategy.
**Status:** Open

## Traceability

**Maps to:** NFR-028 (SQLite Concurrency via WAL Mode — investigation-spurring placeholder), NFR-001/NFR-002 (page load / API response time under concurrent load), C-003 (Single Database), C-004 (self-hostable, no external cloud deps)
**Related:** UC-061 (Automated Backup — WAL sidecar files affect backup procedure), favicon/screenshot cleanup jobs (UC-051/UC-055), batch operations (UC-073/UC-074)

---

## Background

LinkWeave stores all tenant data in a single SQLite file (C-003) accessed through the Agroal JDBC pool. The JDBC URL currently sets `busy_timeout=10000` but runs in the default **rollback-journal** mode (`api/src/main/resources/application.properties:46`). Write contention is therefore a real, growing risk because several write paths can fire concurrently:

- Interactive user writes (create/edit/delete bookmark, drag-and-drop reorder, batch tag/move/delete).
- Scheduled background jobs (favicon cache cleanup UC-051, screenshot cache cleanup UC-055, trashbin retention).
- Metrics/business-counter updates and Flyway-adjacent housekeeping.

SQLite's concurrency model has a hard ceiling that is the crux of this use case:

- **WAL mode** allows *many concurrent readers alongside a single writer*. It does **not** permit multiple concurrent writers — the database still takes one write lock at a time (RESERVED → PENDING → EXCLUSIVE on the whole DB). WAL therefore helps mixed read/write workloads (readers no longer block the writer and vice-versa) but does **not** raise the write ceiling.
- **Rollback-journal mode** (today) additionally makes readers block behind a writer, compounding contention.

So for the *multiple-writers* emphasis of this use case, WAL alone is necessary-but-insufficient, and the evaluation must also cover Turso (libSQL) and application-level write serialization.

### Candidates under evaluation

1. **SQLite WAL mode (stay on SQLite).** One-time `PRAGMA journal_mode=WAL`, tune `synchronous`/`wal_checkpoint`, keep `busy_timeout`. Cheapest change; addresses read concurrency; writers still serialized.
2. **Application-level write serialization.** Funnel all writes through a single dedicated writer (thread/executor or a write queue/dispatcher) so the pool never sees two concurrent write transactions. Pairs with #1. Keeps SQLite; removes writer contention by construction.
3. **Turso / libSQL.** libSQL is the open-source SQLite fork maintained by Turso (ChiselStrike) whose server component (`sqld`) is written in Rust. It adds a native HTTP client protocol, embedded/read replicas, and async replication. **Important:** Turso/libSQL is still primary-replica — writes route to a *single primary* and are serialized there, so it does not by itself grant multiple concurrent writers either. Its value here is decoupling read scaling to replicas, a dedicated server-side write path, and a path to managed/hosted operation. Self-hosted `sqld` would be required to honour C-004 (the managed Turso platform introduces external cloud dependencies and conflicts with C-004). A key integration risk is **JDBC driver maturity**: Quarkus + Hibernate need a JDBC driver, and the official `sqlite-jdbc` (Xerial) used today would be replaced by the community `libsql-jdbc` driver — SQL/feature parity must be verified.
4. **PostgreSQL (heavy option, out of scope unless justified).** True row-level MVCC multi-writer concurrency, but it breaks the C-003 single-file, zero-ops ethos. Carried only as the reference point for what "real" multi-writer would cost; C-003 already notes multi-database architecture may be considered for future scaling.

## Preconditions

- A reproducible concurrency/load test exists (or is built as the first step) that drives simultaneous interactive writes and background jobs against the current rollback-journal configuration, to establish a `SQLITE_BUSY` / latency baseline.
- The Agroal pool sizing and current `busy_timeout` are understood.
- A running Quarkus dev instance with the existing `linkweave.db` for hands-on WAL validation.

## Main Success Scenario

1. Developer builds a repeatable multi-writer load test (concurrent interactive writes + background cleanup jobs + batch operations) and records the baseline: `SQLITE_BUSY` rate, p99 write latency, and reader stalls under rollback-journal mode.
2. Developer enables WAL (e.g. a one-time Flyway migration issuing `PRAGMA journal_mode=WAL`, preferred over per-connection pragmas since WAL persists across connections) and re-runs the same test.
3. Developer confirms WAL's expected behaviour: readers no longer block on the writer and the writer no longer blocks readers; `SQLITE_BUSY` is reduced for mixed read/write loads.
4. Developer confirms the remaining *multiple-writer* gap: concurrent write transactions still serialize, and under heavy write parallelism `SQLITE_BUSY`/timeouts persist beyond what `busy_timeout` smoothing alone can hide.
5. Developer evaluates Turso/libSQL: builds a self-hosted `sqld`/libSQL spike, swaps in the `libsql-jdbc` driver against the Quarkus datasource, verifies Flyway + Hibernate behaviour, and benchmarks the same multi-writer load. Developer documents that writes still serialize at the primary (i.e. libSQL is *not* a multi-writer silver bullet) and records any read-throughput/replication benefits.
6. Developer evaluates application-level write serialization (single-writer executor / write queue) as a low-risk alternative to switching engines, and benchmarks whether it removes writer contention while keeping raw SQLite + WAL.
7. Developer captures durability/crash-recovery, checkpoint strategy, synchronous-mode, backup, and NFS caveats for the recommended option.
8. Developer writes the feasibility report (`docs/sqlite-wal-feasibility.md`) with a recommendation and, if positive, an implementation plan.
9. Report is reviewed; the chosen option is implemented (or explicitly deferred with rationale); NFR-028 is converted into a concrete implementation NFR or closed.

## Alternative Flows

### A1: WAL Is Sufficient for the Expected Load

**Trigger:** Step 4 shows that, at realistically expected concurrency, WAL + a tuned `busy_timeout` keeps `SQLITE_BUSY` and latency within NFR-001/NFR-002 budgets.
**Flow:**

1. Developer documents WAL as the recommendation and closes the Turso/libSQL and write-serialization investigations as "not needed at current scale."
2. Developer implements WAL (migration + checkpoint/synchronous tuning) and updates the backup procedure for `-wal`/`.shm` sidecar files.
3. Use case ends at step 9.

### A2: Multiple Writers Must Scale Beyond a Single Serialized Writer

**Trigger:** Step 4/6 shows write serialization is a hard ceiling that the product will outgrow (e.g. many concurrent batch jobs + interactive users), and neither WAL nor an app-level single-writer queue is enough.
**Flow:**

1. Developer documents that neither SQLite (even with WAL) nor libSQL (primary-replica) provides true concurrent writers.
2. Developer escalates to the PostgreSQL reference option and quantifies the cost against C-003/C-004.
3. A separate decision/ADR is raised to amend C-003 (multi-database) since this exceeds the scope of this use case.
4. Use case ends at step 9 with the escalation recorded.

### A3: Turso/libSQL Adoption Is Blocked by JDBC Integration Risk

**Trigger:** Step 5 finds the `libsql-jdbc` driver is too immature or lacks SQL/Hibernate/Flyway parity.
**Flow:**

1. Developer records the specific gaps (dialect, prepared-statement behaviour, transaction semantics, replication-on/offline behaviour).
2. Developer falls back to WAL + application-level write serialization (Candidate #1 + #2) and records libSQL as "re-evaluate when driver matures."
3. Use case ends at step 9.

### A4: Managed Turso Platform Considered

**Trigger:** The team wants zero-ops hosted operation and is willing to revisit C-004.
**Flow:**

1. Developer flags that the managed Turso platform conflicts with C-004 (self-hostable, no external cloud dependencies).
2. Decision is escalated as a constraints change; self-hosted `sqld` (A-flow of step 5) is used in the meantime if adopted at all.
3. Use case continues at step 8 with the constraint tension explicitly documented.

## Postconditions

### Success Postconditions

- A feasibility report exists at `docs/sqlite-wal-feasibility.md` with a clear recommendation covering WAL, multiple-writer behaviour, and the Turso/libSQL verdict.
- Whatever option is chosen is either implemented, or explicitly deferred with a recorded rationale and a load-test baseline.
- NFR-028 is resolved (converted to a concrete implementation NFR, or closed).
- If WAL is adopted, the backup procedure (UC-061) and deployment guide are updated for sidecar files and the NFS caveat.

### Failure Postconditions

- No recommendation is produced; `SQLITE_BUSY` risk under multi-user + background-job load remains unquantified, and NFR-028 stays open.

## Business Rules

### BR-095-1: WAL Does Not Create Multiple Writers

Any recommendation must state explicitly that WAL improves *read* concurrency and reader/writer non-blocking, but that SQLite (and libSQL/Turso's primary) still serializes writes to a single writer. "Enable WAL" must not be presented as a solution to concurrent writes on its own.

### BR-095-2: Decision Must Be Evidence-Based

The recommendation must be backed by the step-1/step-2/step-5/step-6 load-test numbers (baseline vs each candidate) against the same multi-writer workload, not by general SQLite/Turso marketing claims.

### BR-095-3: Honour C-003 and C-004

Adopting the managed Turso platform is treated as a constraints change (it conflicts with C-004) and must be escalated, not silently introduced. Any move off the single SQLite file (e.g. PostgreSQL) similarly requires revisiting C-003.

### BR-095-4: Self-Hosted Bias

Because LinkWeave is self-hosted and zero-ops (C-004), the default preference order is: WAL (Candidate #1) → WAL + app-level write serialization (#2) → self-hosted `sqld`/libSQL (#3) → PostgreSQL (#4). Options requiring external cloud services are deprioritized.

### BR-095-5: Backup and Durability Must Be Re-Specified

If WAL is adopted, the checkpoint strategy (`PASSIVE`/`FULL`/`TRUNCATE`), `synchronous` mode (`NORMAL`/`FULL`), and the requirement that backups capture `.db` + `-wal` + `.shm` together must be documented, and the NFS/network-volume caveat must be added to the deployment guide.

---

## Evaluation Criteria / Decision Matrix

| Criterion                                     | WAL (stay on SQLite) | WAL + single-writer queue | Self-hosted libSQL (`sqld`) | PostgreSQL |
|-----------------------------------------------|:--------------------:|:-------------------------:|:---------------------------:|:----------:|
| Read concurrency improvement                  | High                 | High                      | High (+ replicas)           | High       |
| Multiple concurrent writers                   | No (serialized)      | No (by design)            | No (primary-serialized)     | **Yes**    |
| Mitigates `SQLITE_BUSY` under mixed load      | High                 | High                      | High                        | High       |
| Implementation effort / risk                  | Low                  | Medium                    | Medium-High (JDBC risk)     | High       |
| Keeps C-003 (single file)                     | Yes                  | Yes                       | Partial                     | No         |
| Keeps C-004 (self-hosted, no cloud)           | Yes                  | Yes                       | Yes                         | Yes        |
| Operational complexity                        | Minimal              | Low                       | Medium                      | High       |

## Implementation Notes

- **Baseline first.** Do not choose an option without the step-1 load test; the whole use case hinges on whether realistic concurrency actually hits the write ceiling.
- **WAL via migration, not per-connection pragma.** `journal_mode=WAL` persists in the DB header, so issue it once via Flyway rather than on every pooled connection.
- **`busy_timeout` tuning.** The current 10000 ms is a long stall; pair WAL with a shorter, bounded timeout plus app-level retry (and/or the single-writer queue) rather than relying on a 10 s block.
- **Deliverable.** The feasibility report (`docs/sqlite-wal-feasibility.md`) named in NFR-028 is the artifact this use case produces; it should contain the baseline numbers, the matrix above filled in with real data, and a single recommendation.
