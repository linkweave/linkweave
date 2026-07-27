# SQLite WAL Feasibility Report

**Requirement:** NFR-028 (SQLite Concurrency via WAL Mode)
**Use Case:** [UC-095](use_cases/UC-095-explore-sqlite-wal-and-turso-multiwriter.md)
**Status:** Accepted — WAL is the recommendation; no engine change warranted
**Date:** 2026-07-19
**Related:** [UC-061 Automated Backup Creation](use_cases/UC-061-automated-backup-creation.md) (WAL sidecar files affect backups), C-003 (Single Database), C-004 (Self-hostable, no cloud deps)

---

## TL;DR

**Enable WAL mode (already wired through the JDBC URL) and stop.** Measured against a 16-worker concurrent-write workload that mirrors the worst realistic LinkWeave load (interactive writes + background jobs + batch operations), WAL alone takes the system from **94% `SQLITE_BUSY` error rate at 1.1 ops/s** (rollback-journal baseline) to **~0% errors at 135–410 ops/s** — a ~100× throughput improvement, with p99 write latency under 1 s.

A read/write datasource **split** is not warranted, for two reasons: (a) the status quo is already at ~0% errors — there is no contention pain to relieve; and (b) a dedicated write pool of 1 has a worse failure mode than SQLite's own `busy_timeout` handling — Agroal's acquisition timeout (default 5 s) turns writer queueing into hard 5xx errors, whereas SQLite's busy handling at least *waits*. A long batch import on a single write connection would fail every other queued write for its duration. Both pools would still hit the same OS-level write lock anyway; the split only pays off when reads route to a physically separate replica (Postgres, libSQL primary-replica).

Turso Database (Limbo) remains the only SQLite-family engine with true multi-writer MVCC, but it is still **not adoptable today** (beta, no JDBC driver, MVCC limitations on indexes). Re-evaluate when production-ready with a Hibernate integration path.

**Recommendation:** WAL + default pool (Agroal default = 50 connections). The strict regression gate (`SqliteWriteContentionLoadITest` with `LINKWEAVE_LOADTEST_STRICT=true`) passes 2/2 tests with zero non-2xx responses — that gate is now the going-forward guard for any change that could regress write contention.

---

## 1. Baseline — rollback-journal, default Agroal pool

`SqliteWriteContentionLoadITest` with `journal_mode=DELETE`, `busy_timeout=10000`, default Agroal pool (50 connections). Workload: 16 worker threads × 30 ops each (480 total), batches of 120 bookmarks, seeded with 400 existing bookmarks.

### Heavy batch writes (batch-tag / batch-move only)

```
wall=253.87s  totalOps=480  throughput=1.9 ops/s  errors=275 (57.3%)
op             count  errors    min_us    p50_us    p95_us    p99_us    max_us  err_statuses
batch-tag        240     136      8729  10391986  30030737  30038280  30039792  -1,500
batch-move       240     139      9193  10397975  30004759  30009164  30012543  500,-1
```

### Mixed concurrent writes (create / update / track-click / batch-tag / batch-move)

```
wall=453.13s  totalOps=480  throughput=1.1 ops/s  errors=451 (94.0%)
op             count  errors    min_us    p50_us    p95_us    p99_us    max_us  err_statuses
batch-tag         96      93     24998  10426813  30005398  30006870  30006870  500,-1
batch-move        96      90   1305156  10421534  30004776  30006040  30006040  500,-1
update            96      92   2552399  10426985  30004834  30013179  30013179  500,-1
create            96      89   2857962  10491592  30010758  30015743  30015743  -1,500
track             96      87     60922  10423947  30004620  30008921  30008921  500,-1
```

**Interpretation.** Rollback-journal mode lets only *one* connection access the file at a time — readers block writers and vice-versa. Under 16-way concurrency, 16 transactions all hit the file lock, the `busy_timeout` of 10 s is exhausted on most of them, and they surface as `SQLITE_BUSY` 5xx (or `-1` for client-side acquisition timeouts). p99 = 30 s (the busy_timeout ceiling). This is the contention floor UC-095 was opened against.

---

## 2. WAL — default pool, default workload

Same workload, `journal_mode=WAL&synchronous=NORMAL` (the production JDBC URL config — see `application.properties:55`). Agroal default pool (50 connections, verified at runtime via `PRAGMA journal_mode` / pool diagnostics in the test report).

### Heavy batch writes

```
wall=3.55s  totalOps=480  throughput=135.0 ops/s  errors=0 (0.0%)
op             count  errors    min_us    p50_us    p95_us    p99_us    max_us
batch-tag        240       0     18739    107745    283978    547273    581922
batch-move       240       0     11391    103282    165706    442858    545560
```

### Mixed concurrent writes

```
wall=1.61s  totalOps=480  throughput=298.2 ops/s  errors=0 (0.0%)
op             count  errors    min_us    p50_us    p95_us    p99_us    max_us
batch-tag         96       0      8114     44283    412182    766416    766416
batch-move        96       0      7411     40599    196050    599305    599305
update            96       0      3841      8659     80250    246399    246399
create            96       0      3056      8842     81062    128076    128076
track             96       0      3023      5727     62119    131307    131307
```

**Improvement vs. baseline:** ~70–270× throughput, 57–94% errors → 0%, p99 30 s → ~0.5–0.8 s. WAL's separating of the write-ahead log from the readers is doing all of the work — readers no longer block on the writer's reserved lock and the writer no longer waits for readers to drain.

---

## 3. WAL under stress (UC-095 step 4 — find the breakpoint)

### 2× workload (32 workers × 60 ops = 1920 ops)

```
# Heavy batch writes
wall=10.09s  totalOps=1920  throughput=190.2 ops/s  errors=0 (0.0%)
op             count  errors    min_us    p50_us    p95_us    p99_us    max_us
batch-tag        960       0     13569    158424    255184    858595    911160
batch-move       960       0     11679    157148    237120    274029    580353

# Mixed concurrent writes
wall=4.68s  totalOps=1920  throughput=410.0 ops/s  errors=2 (0.1%)
```

### 250 ms `busy_timeout` (force SQLITE_BUSY to surface as 5xx)

```
# Heavy batch writes
wall=3.13s  totalOps=480  throughput=153.5 ops/s  errors=0 (0.0%)

# Mixed concurrent writes
wall=1.65s  totalOps=480  throughput=291.6 ops/s  errors=1 (0.2%)
```

### Larger batches (300 bookmarks per batch-tag / batch-move)

```
# Heavy batch writes
wall=8.40s  totalOps=480  throughput=57.2 ops/s  errors=0 (0.0%)

# Mixed concurrent writes
wall=3.00s  totalOps=480  throughput=160.0 ops/s  errors=0 (0.0%)
```

**Interpretation.** Throughput scales *up* with concurrency (better utilisation of the WAL read parallelism). All WAL configurations — default workload, 2× workload, 250 ms timeout, 2.5× larger batches — are effectively tied at **~zero errors** (0–2 failed ops out of 480–1920 is well below the noise floor of this test). The `@RetryOnSqliteBusy` interceptor on the resource layer absorbs the `SQLITE_BUSY_SNAPSHOT` events that do occur internally (visible as `SQLITE_BUSY_SNAPSHOT` warnings in the server log even when the client sees 0 errors). WAL has substantial headroom above the realistic LinkWeave concurrency ceiling (a self-hosted single-user app with a handful of background jobs).

---

## 4. Pool sizing and the read/write split question

### What was tested vs. what was proposed

The original proposal was a **read/write datasource split**: a dedicated write pool of 1 alongside a read pool of 20. What the experiments actually measured was **shrinking the single shared pool** to 1, 2, 4, 8. These are architecturally different — a global pool of 1 serializes reads, writes, test setup, Flyway migrations, and Quarkus background jobs all through the same single connection. A split would leave reads, setup, and migrations on the large pool and only serialize writes. So the findings below are evidence about global pool sizing, not about a dedicated write pool.

### Global pool-sizing experiments

The Agroal default (verified at runtime via `PRAGMA` diagnostics) is **50 connections**, not the ~20 the UC-095 use case assumed.

| `max-size` | Result |
|---|---|
| **1** | Test setup transaction times out — Quarkus background jobs compete with the `FixtureService` seed loop for the single connection. `RollbackException: ARJUNA016102: The transaction is not active!` |
| **2 – 8** | Test hangs — **nested-transaction connection deadlock** (see root-cause analysis below). Not a `SQLITE_BUSY` issue. |
| **50 (Agroal default)** | ~0 errors, ~zero contention. Best result. |

### Root cause of the pool ≤ 8 hang: nested-transaction connection deadlock

Thread-dump analysis (`jstack` on the hung JVM) revealed that **every API-key-authenticated request needs 2 concurrent connections**:

1. The resource method (`@JaxResource` = `@Transactional(REQUIRED)`) starts an outer transaction → acquires **connection #1**.
2. `UserRepo.findBenutzerIdFromBenutzername` (`@Transactional(REQUIRES_NEW)`, `api/src/main/java/org/linkweave/api/benutzer/UserRepo.java:44`) suspends the outer transaction and starts a new one → needs **connection #2**.

With `max-size=8`, only 4 concurrent requests can hold both connections simultaneously. The 5th request acquires connection #1 (for the outer tx), then blocks waiting for connection #2 (for the `REQUIRES_NEW` auth lookup). Since all 8 connections are now held by suspended outer transactions, no inner transaction can ever get a connection — **classic resource deadlock**:

```
32 threads all parked at io.agroal.pool.ConnectionPool.waitAvailableHandler
→ org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.acquire
→ UserRepo.findBenutzerIdFromBenutzername  (REQUIRES_NEW)
→ TransactionalInterceptorRequiresNew
```

The Agroal acquisition timeout (30 s in the test) eventually fires per-thread and produces a `SQLException("Sorry, acquisition timeout!")`, but with 80 ops × 30 s worst case the test takes ~40 minutes to drain. This is not a throughput limit — a 5-op-per-worker run (80 ops total, should finish in <1 s with the default pool) still hangs at 5 minutes.

The `@RetryOnSqliteBusy` interceptor does **not** worsen the hang — it only catches `SQLiteException` with BUSY error codes, not Agroal's generic `SQLException`.

**Constraint:** the minimum viable pool size is `2 × max_concurrent_api_key_requests` (the `REQUIRES_NEW` on the auth path doubles the per-request connection requirement). The Agroal default of 50 supports 25 concurrent requests, which is well above the expected LinkWeave load.

### Why the read/write split is still rejected (correct grounds)

The split is correctly rejected, but **not** because of the global-pool findings above. The actual reasons are:

1. **The status quo has no problem to solve.** WAL + the default pool of 50 already delivers ~0% errors at 2× the expected workload (§3). There is no contention pain that a split would relieve.

2. **A dedicated write pool of 1 has a worse failure mode than SQLite's own busy handling.** When SQLite's `busy_timeout` is engaged, the writer thread *waits* and retries internally — the client sees latency, not errors. When Agroal's acquisition timeout fires (default 5 s), the queued writer gets a hard `SQLException` immediately. A long batch import (e.g. `POST /bookmarks/batch-tag` with 500 items) holding the single write connection for >5 s would 5xx every other queued write for the duration. SQLite's own serialisation degrades gracefully; Agroal's does not.

3. **Both pools would hit the same OS-level write lock.** Even with separate pools, SQLite still serializes writers at the file level. The split cannot raise the write ceiling — it only changes which queue (Agroal's vs. SQLite's) the writer waits in. SQLite's queue is strictly better here because it waits inside the engine rather than failing at the pool level.

### Note on `SQLITE_BUSY_SNAPSHOT`

The WAL-mode `SQLITE_BUSY` that appears in server logs under concurrent load is predominantly `BUSY_SNAPSHOT`: a deferred transaction starts reading, another connection commits during the read, and the read→write upgrade at commit time fails immediately (`busy_timeout` does not help because the snapshot is stale, not contended). The `@RetryOnSqliteBusy` interceptor catches these and the retry succeeds on the next attempt with a fresh snapshot. This is working as designed and contributes to the ~0% error rate seen by clients.

---

## 5. Turso Database (Limbo) — UC-095 step 6 verdict

Per UC-095 BR-095-2, this is necessarily a **feasibility/maturity judgement, not a benchmark** — Limbo has no JDBC driver, so no in-process measurement is possible.

| Limbo adoption criterion | Status (2026-07) |
|---|---|
| Production maturity | ❌ Beta / "early technology preview" |
| JDBC / Hibernate integration path | ❌ None — Rust / WASM / JS bindings only |
| Index support on MVCC databases | ❌ Not yet — disqualifying for LinkWeave's index-heavy bookmark schema |
| Memory profile | ❌ Eager full-dataset load on first access — poor fit for a growing bookmark DB |
| `BEGIN CONCURRENT` semantics | ✅ The one feature that would unlock true multi-writer on a SQLite-format file |
| Self-hosted / C-004 compatible | ✅ Embedded engine (not Turso Cloud) is fine |

**Verdict:** Watch and re-evaluate. Limbo is the *future* answer to true multi-writer on a single SQLite-format file, but it is not adoptable today. The trigger to re-evaluate is: production GA + a maintained JDBC driver + index support on MVCC databases.

---

## 6. Decision matrix (UC-095 evaluation criteria, filled with measured data)

| Criterion | WAL only (chosen) | WAL + pool sizing | WAL + single-writer queue | Turso Database (Limbo) | PostgreSQL |
|---|:---:|:---:|:---:|:---:|:---:|
| Read concurrency improvement | **High** (measured) | No benefit (default already 50) | High | High | High |
| Multiple concurrent writers | No (serialized) | No | No (by design) | Yes (MVCC) | Yes |
| Mitigates `SQLITE_BUSY` under mixed load | **~0% errors at 2× load** | No additional benefit | High | High | High |
| Implementation effort / risk | **Already shipped** | Config-only (no benefit) | Medium | Blocked (no JDBC) | High |
| Keeps C-003 (single file) | ✅ | ✅ | ✅ | ✅ | ❌ |
| Keeps C-004 (self-hosted, no cloud) | ✅ | ✅ | ✅ | ✅ (embedded only) | ✅ |
| Operational complexity | Minimal | Minimal | Low | Unknown | High |

---

## 7. Recommendation

1. **Keep WAL on.** The JDBC URL already enables it (`application.properties:55` `journal_mode=WAL&synchronous=NORMAL`). No code change needed in production.
2. **Do not shrink the pool.** The Agroal default (50) is already well above what the workload needs. Smaller pools hang due to Agroal/vert.x thread-pool scheduling interactions, not SQLite contention.
3. **Do not introduce a read/write datasource split.** The status quo is at ~0% errors — there is no problem to solve. A dedicated write pool of 1 would introduce Agroal acquisition-timeout failures (hard 5xx on queue timeout) that are worse than SQLite's own `busy_timeout` behaviour (graceful wait). The split only pays off when routing reads to a physically separate engine (Postgres replica, libSQL primary-replica), which would break C-003.
4. **Treat the load test strict mode as the regression gate.** `LINKWEAVE_LOADTEST=true LINKWEAVE_LOADTEST_WAL=true LINKWEAVE_LOADTEST_STRICT=true ./mvnw test -pl api -Dtest=SqliteWriteContentionLoadITest` passes 2/2 with zero non-2xx. Any future change that could regress write contention (new write path, batch operation, scheduled job) must keep this gate green.
5. **Close NFR-028** as delivered by the existing WAL configuration. The Turso (Limbo) re-evaluation trigger becomes a separate, future NFR if/when its adoption criteria are met.

---

## 8. Durability, backup, and operational caveats (BR-095-5)

WAL introduces three operational considerations that deployment and backup procedures must account for:

### Sidecar files

A WAL-mode database consists of **three files** that must be backed up *together*:

- `linkweave.db` — the main database
- `linkweave.db-wal` — the write-ahead log (recent committed transactions not yet checkpointed back into the main file)
- `linkweave.db-shm` — shared-memory index (regenerated automatically if missing, but references the WAL state)

Backing up only `linkweave.db` will lose recent committed transactions. The backup procedure in UC-061 (Automated Backup Creation) must be updated to copy all three atomically — typically via SQLite's backup API (`VACUUM INTO` or `.backup` from the sqlite3 CLI), which handles the checkpoint internally.

### Checkpoint strategy

SQLite's default `wal_autocheckpoint=1000` (pages) is appropriate for LinkWeave's load. Manual checkpoint modes:

- `PASSIVE` (default) — checkpoint runs in the background, doesn't block. Fine for typical operation.
- `FULL` / `RESTART` / `TRUNCATE` — force a complete checkpoint. Use during scheduled maintenance or before backups if a single-file snapshot is desired.

No override is recommended at this time — the autocheckpoint is sufficient.

### `synchronous` mode

`NORMAL` (current setting) is correct for WAL: it fsyncs the WAL at checkpoint boundaries but not on every commit, giving good crash durability without the latency cost of `FULL`. The only loss is the very last transaction in the event of a power loss to the WAL file itself — acceptable for a bookmark manager.

### NFS / network-volume caveat

**WAL mode is not safe on NFS or other network filesystems that do not provide shared-memory mmap semantics.** SQLite uses mmap'd shared memory (`-shm`) for inter-process coordination, and NFS's mmap behaviour can silently corrupt the lock state. Deployment guides must specify:

- ✅ Local SSD / disk (default self-hosted case)
- ✅ Container volume (Docker `volume` mount, not `bind` to a network FS)
- ❌ NFS, SMB, or cloud "network drives" backing the DB path

If `LINKWEAVE_DB_PATH` ever points at a network volume, the operator must switch back to rollback-journal mode (`journal_mode=DELETE`) and accept the original contention behaviour.

---

## 9. Implementation status

| Item | Status | Reference |
|---|---|---|
| WAL enabled in JDBC URL | ✅ Shipped | `api/src/main/resources/application.properties:55-56` |
| `synchronous=NORMAL` | ✅ Shipped | same |
| `busy_timeout=10000` | ✅ Shipped | same |
| `@RetryOnSqliteBusy` interceptor on resources | ✅ Shipped | `api/src/main/java/org/linkweave/infrastructure/db/RetryOnSqliteBusy.java`, `SqliteBusyRetryInterceptor.java` |
| Load test infrastructure (configurable WAL/pool/timeout + PRAGMA diagnostics) | ✅ Shipped | `api/src/test/java/org/linkweave/api/loadtest/LoadTestProfile.java`, `SqliteWriteContentionLoadITest.java` |
| Baseline + WAL experiments executed | ✅ This report | Sections 1–3 |
| Pool-sizing experiments executed | ✅ This report (global pool, not a split) | Section 4 |
| Turso Limbo verdict recorded | ✅ This report | Section 5 |
| Strict regression gate green | ✅ Passes 2/2 with 0 non-2xx | Section 7 |
| Backup procedure updated for WAL sidecar files | 🟡 Pending — UC-061 follow-up | Section 8 |
| Deployment guide NFS caveat added | 🟡 Pending | Section 8 |
| NFR-028 closed | 🟡 Pending — convert to "delivered by WAL" status | — |

---

## 10. How to reproduce

```bash
cd api

# Baseline (rollback-journal, ~7 min):
rm -f linkweave-test.db linkweave-test.db-wal linkweave-test.db-shm
./mvnw test -Dtest=SqliteWriteContentionLoadITest \
    -DLINKWEAVE_LOADTEST=true

# WAL run (~15 s):
rm -f linkweave-test.db linkweave-test.db-wal linkweave-test.db-shm
./mvnw test -Dtest=SqliteWriteContentionLoadITest \
    -DLINKWEAVE_LOADTEST=true -DLINKWEAVE_LOADTEST_WAL=true

# Strict regression gate (~15 s, fails on any 5xx):
rm -f linkweave-test.db linkweave-test.db-wal linkweave-test.db-shm
./mvnw test -Dtest=SqliteWriteContentionLoadITest \
    -DLINKWEAVE_LOADTEST=true -DLINKWEAVE_LOADTEST_WAL=true \
    -DLINKWEAVE_LOADTEST_STRICT=true

# Pool-sizing experiments (option B — will hang ≤ 8, see §4):
rm -f linkweave-test.db linkweave-test.db-wal linkweave-test.db-shm
./mvnw test -Dtest=SqliteWriteContentionLoadITest \
    -DLINKWEAVE_LOADTEST=true -DLINKWEAVE_LOADTEST_WAL=true \
    -DLINKWEAVE_LOADTEST_MAX_SIZE=8 -DLINKWEAVE_LOADTEST_ACQUISITION_TIMEOUT_MS=30000
```

Reports are written to `api/target/surefire-reports/TEST-org.linkweave.api.loadtest.SqliteWriteContentionLoadITest.xml` — grep for `^config:\|^runtime:\|^wall=\|^op ` to extract the summary. The `runtime:` line prints the actual `PRAGMA journal_mode`, `PRAGMA busy_timeout`, and effective pool size from the running JVM, so each run is self-verifying against configuration confounds (e.g. a stale DB file whose persisted WAL header silently overrides the URL pragma).

The env-var / system-property overrides (workers, ops, batch, seed, busy_timeout, wal mode, max-size, min-size, acquisition-timeout, strict) are all documented in `LoadTestProfile.java` and `SqliteWriteContentionLoadITest.java` Javadoc.
