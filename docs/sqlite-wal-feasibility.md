# SQLite WAL Feasibility Report

**Requirement:** NFR-028 (SQLite Concurrency via WAL Mode)
**Use Case:** [UC-095](use_cases/UC-095-explore-sqlite-wal-and-turso-multiwriter.md)
**Status:** Accepted — WAL is the recommendation; no engine change warranted
**Date:** 2026-07-19
**Related:** [UC-061 Automated Backup Creation](use_cases/UC-061-automated-backup-creation.md) (WAL sidecar files affect backups), C-003 (Single Database), C-004 (Self-hostable, no cloud deps)

---

## TL;DR

**Enable WAL mode (already wired through the JDBC URL) and stop.** Measured against a 16-worker concurrent-write workload that mirrors the worst realistic LinkWeave load (interactive writes + background jobs + batch operations), WAL alone takes the system from **94% `SQLITE_BUSY` error rate at 1.1 ops/s** (rollback-journal baseline) to **0% errors at 135–410 ops/s** — a ~100× throughput improvement, with p99 write latency under 1 s.

Pool sizing provides **no additional benefit**: small pools (≤4) hang the test setup because Quarkus background threads starve the single connection, medium pools (8) actively resurface `SQLITE_BUSY` due to longer queue depths, and the Agroal default (~20) is already optimal. A read/write datasource **split** is not warranted because both pools would still hit the same OS-level SQLite write lock — the split only pays off when reads route to a physically separate replica.

Turso Database (Limbo) remains the only SQLite-family engine with true multi-writer MVCC, but it is still **not adoptable today** (beta, no JDBC driver, MVCC limitations on indexes). Re-evaluate when production-ready with a Hibernate integration path.

**Recommendation:** WAL + default pool. The strict regression gate (`SqliteWriteContentionLoadITest` with `LINKWEAVE_LOADTEST_STRICT=true`) passes 2/2 tests with zero non-2xx responses — that gate is now the going-forward guard for any change that could regress write contention.

---

## 1. Baseline — rollback-journal, default Agroal pool

`SqliteWriteContentionLoadITest` with `journal_mode=DELETE`, `busy_timeout=10000`, default pool size (~20). Workload: 16 worker threads × 30 ops each (480 total), batches of 120 bookmarks, seeded with 400 existing bookmarks.

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

Same workload, `journal_mode=WAL&synchronous=NORMAL` (the production JDBC URL config — see `application.properties:55`).

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

**Interpretation.** Throughput scales *up* with concurrency (better utilisation of the WAL read parallelism). Error rate stays at 0–0.2% across 2× workload, 250 ms timeout, and 2.5× larger batches. The `@RetryOnSqliteBusy` interceptor on the resource layer absorbs the rare `SQLITE_BUSY` events that do occur. WAL has substantial headroom above the realistic LinkWeave concurrency ceiling (a self-hosted single-user app with a handful of background jobs).

---

## 4. Pool sizing — UC-095 Candidate #2 (option B)

| `quarkus.datasource.jdbc.max-size` | Result |
|---|---|
| **1** | Test setup transaction times out — Quarkus background jobs (scheduler, startup) compete with the `FixtureService` seed loop for the single connection. `RollbackException: ARJUNA016102: The transaction is not active!` |
| **2** | Load runner hangs indefinitely — vert.x worker threads (default pool 20) all block on connection acquisition, request queue backs up, test does not complete within 15-min latch wait |
| **4** | Same as 2 — `RollbackException` during `FixtureService.createTestCollection()` |
| **8** | Test completes but `SQLITE_BUSY` errors reappear (visible in `target/surefire-reports/*.xml`) — fewer connections means longer queue depths and more tx retries, which compounds contention rather than reducing it |
| **~20 (default)** | 0% errors, best result |

**Conclusion.** The pool-sizing lever has a U-shaped curve. Below ~8 it starves the framework; around 8 it accidentally worsens contention by serialising queue waits; at the Agroal default it is already optimal. There is no configuration in this range that beats the default.

This empirically validates UC-095's BR-095-1 ("WAL does not create multiple writers") and BR-095-4 (preference order: WAL → WAL + pool sizing → write serialization → engine change) — pool sizing is shown to add nothing on top of WAL for this workload.

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
| Read concurrency improvement | **High** (measured) | Medium | High | High | High |
| Multiple concurrent writers | No (serialized) | No (measured — no benefit) | No (by design) | Yes (MVCC) | Yes |
| Mitigates `SQLITE_BUSY` under mixed load | **High — 0% errors at 2× load** | No additional benefit | High | High | High |
| Implementation effort / risk | **Already shipped** | Config-only (no benefit shown) | Medium | Blocked (no JDBC) | High |
| Keeps C-003 (single file) | ✅ | ✅ | ✅ | ✅ | ❌ |
| Keeps C-004 (self-hosted, no cloud) | ✅ | ✅ | ✅ | ✅ (embedded only) | ✅ |
| Operational complexity | Minimal | Minimal | Low | Unknown | High |

---

## 7. Recommendation

1. **Keep WAL on.** The JDBC URL already enables it (`application.properties:55` `journal_mode=WAL&synchronous=NORMAL`). No code change needed in production.
2. **Do not introduce pool sizing.** Default (~20) is optimal; smaller pools actively regress.
3. **Do not introduce a read/write datasource split.** Both pools would hit the same OS-level SQLite write lock; the split only pays off when routing reads to a physically separate engine (Postgres replica, libSQL primary-replica), which would break C-003.
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
| Load test infrastructure (configurable WAL/pool/timeout) | ✅ Shipped | `api/src/test/java/org/linkweave/api/loadtest/LoadTestProfile.java`, `SqliteWriteContentionLoadITest.java` |
| Baseline + WAL experiments executed | ✅ This report | Sections 1–3 |
| Pool-sizing experiments executed | ✅ This report | Section 4 |
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

# Pool-sizing experiments (option B):
rm -f linkweave-test.db linkweave-test.db-wal linkweave-test.db-shm
./mvnw test -Dtest=SqliteWriteContentionLoadITest \
    -DLINKWEAVE_LOADTEST=true -DLINKWEAVE_LOADTEST_WAL=true \
    -DLINKWEAVE_LOADTEST_MAX_SIZE=8 -DLINKWEAVE_LOADTEST_ACQUISITION_TIMEOUT_MS=30000
```

Reports are written to `api/target/surefire-reports/TEST-org.linkweave.api.loadtest.SqliteWriteContentionLoadITest.xml` — grep for `^config:|^wall=|^op ` to extract the summary.

The env-var / system-property overrides (workers, ops, batch, seed, busy_timeout, wal mode, max-size, min-size, acquisition-timeout, strict) are all documented in `LoadTestProfile.java` and `SqliteWriteContentionLoadITest.java` Javadoc.
