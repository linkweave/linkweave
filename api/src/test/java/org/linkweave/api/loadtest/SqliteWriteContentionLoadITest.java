package org.linkweave.api.loadtest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load test that reproduces the SQLite write contention described in UC-095
 * (and drives the NFR-028 baseline measurement).
 *
 * <h2>What it does</h2>
 * Spawns many concurrent worker threads that hammer the real bookmark write endpoints
 * (create / update / track-click / batch-tag / batch-move) through the Agroal pool against
 * the single SQLite file. Because SQLite serializes all writers, concurrent write transactions
 * contend for the single write lock — manifesting either as {@code SQLITE_BUSY} errors (5xx) or,
 * with the production {@code busy_timeout=10000}, as exploding p99 latency from the writer queue.
 *
 * <h2>Running it</h2>
 * Gated behind an environment variable so normal {@code ./mvnw verify} is unaffected (the class
 * is reported as disabled, not run). Env vars are used (rather than {@code -D} system properties)
 * because they propagate reliably to the forked test JVM. Note: {@code *ITest} classes match
 * surefire's {@code *Test} pattern, so select the class with {@code -Dtest=} (not failsafe's
 * {@code -Dit.test=}, which would run the whole suite).
 *
 * <pre>
 * # Baseline (rollback-journal, prod-like busy_timeout):
 * LINKWEAVE_LOADTEST=true ./mvnw test -pl api -Dtest=SqliteWriteContentionLoadITest
 *
 * # Production WAL config / WAL plus transaction_mode=IMMEDIATE (see LoadTestProfile):
 * LINKWEAVE_LOADTEST=true LINKWEAVE_LOADTEST_WAL=true ./mvnw test -pl api -Dtest=SqliteWriteContentionLoadITest
 * LINKWEAVE_LOADTEST=true LINKWEAVE_LOADTEST_WAL=immediate ./mvnw test -pl api -Dtest=SqliteWriteContentionLoadITest
 *
 * # Force SQLITE_BUSY to surface as 5xx quickly (short busy_timeout):
 * LINKWEAVE_LOADTEST=true LINKWEAVE_LOADTEST_BUSY_TIMEOUT_MS=250 \
 *     ./mvnw test -pl api -Dtest=SqliteWriteContentionLoadITest
 *
 * # Tune the workload:
 * LINKWEAVE_LOADTEST=true LINKWEAVE_LOADTEST_WORKERS=32 LINKWEAVE_LOADTEST_OPS=60 \
 *     LINKWEAVE_LOADTEST_BATCH=300 LINKWEAVE_LOADTEST_SEED=1000 ...
 *
 * # After a mitigation (single-writer queue / retry-on-busy) lands, flip to a strict regression gate:
 * LINKWEAVE_LOADTEST=true LINKWEAVE_LOADTEST_STRICT=true ./mvnw test -pl api -Dtest=SqliteWriteContentionLoadITest
 * </pre>
 *
 * <h2>Interpreting the output</h2>
 * The report prints per-op count, error count, p50/p95/p99/max latency and the distinct error
 * status codes. By default the test stays green while SURFACING the contention numbers (errors are
 * expected). Set {@code LINKWEAVE_LOADTEST_STRICT=true} to fail on any non-2xx — use this after a
 * fix to ensure contention stays gone.
 *
 * <h2>Auth</h2>
 * Each worker authenticates with its own {@code X-API-Key} header (stateless, per-request), which is
 * bulletproof under concurrency. The key is created once during setup via {@code POST /auth/api-keys}
 * as {@code test@example.com}, so all worker requests run as the collection owner with
 * {@code BOOKMARK_WRITE}.
 */
@QuarkusTest
@TestProfile(LoadTestProfile.class)
@EnabledIfEnvironmentVariable(named = "LINKWEAVE_LOADTEST", matches = "true")
class SqliteWriteContentionLoadITest {

    private static final int CREATE = 0;
    private static final int UPDATE = 1;
    private static final int TRACK = 2;
    private static final int BATCH_TAG = 3;
    private static final int BATCH_MOVE = 4;
    private static final int BATCH_MAX = 500;

    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    private final int workers = env("LINKWEAVE_LOADTEST_WORKERS", 16);
    private final int opsPerWorker = env("LINKWEAVE_LOADTEST_OPS", 30);
    private final int batchSize = env("LINKWEAVE_LOADTEST_BATCH", 120);
    private final int seed = Math.max(env("LINKWEAVE_LOADTEST_SEED", 400), batchSize);

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldMeasureMixedConcurrentWriteBaseline() throws InterruptedException {
        // ARRANGE
        Setup setup = setUp();
        // ACT
        RunResult result = runConcurrent(setup, i -> i % 5);
        // ASSERT
        report("UC-095 baseline: mixed concurrent writes (rollback-journal)", result);
        softAssert(result);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReproduceContentionWithHeavyBatchWrites() throws InterruptedException {
        // ARRANGE
        Setup setup = setUp();
        // ACT — long-running batch write transactions are the most reliable SQLITE_BUSY trigger
        RunResult result = runConcurrent(setup, i -> (i % 2 == 0) ? BATCH_TAG : BATCH_MOVE);
        // ASSERT
        report("UC-095 contention: heavy batch writes (long write txns)", result);
        softAssert(result);
    }

    // ---- setup -----------------------------------------------------------------

    private record Setup(String collectionId, String apiKey, List<String> bookmarkIds, String tagId,
                         String folderId) {
    }

    private Setup setUp() {
        databaseService.resetDatabase();
        Collection collection = fixtureService.createTestCollection();

        List<String> ids = new ArrayList<>(seed);
        for (int i = 0; i < seed; i++) {
            final int idx = i;
            Bookmark b = fixtureService.persistBookmark(x -> x
                .withCollection(collection)
                .withTitle("seed-" + idx)
                .withUrl("https://example.com/seed-" + idx));
            ids.add(b.getId().getUUID().toString());
        }
        Tag tag = fixtureService.persistTag(t -> t.withCollection(collection).withName("loadtag").withColor("#123456"));
        Folder folder = fixtureService.persistFolder(f -> f.withCollection(collection).withName("loadfolder"));
        String collectionId = collection.getId().getUUID().toString();

        String apiKey = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"LoadTestKey\"}")
            .post("/auth/api-keys")
            .then().statusCode(201).extract().path("key");

        // Precondition: the API key authenticates and reaches an owned resource.
        RestAssured.given()
            .header("X-API-Key", apiKey)
            .get("/collections")
            .then().statusCode(200);

        return new Setup(collectionId, apiKey, ids,
            tag.getId().getUUID().toString(), folder.getId().getUUID().toString());
    }

    // ---- load runner -----------------------------------------------------------

    private record OpResult(String op, int status, long micros, String error) {
        boolean success() {
            return status >= 200 && status < 300;
        }
    }

    private record RunResult(long wallMicros, ConcurrentLinkedQueue<OpResult> results) {
    }

    private RunResult runConcurrent(Setup setup, IntUnaryOperator modeFn) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        ConcurrentLinkedQueue<OpResult> results = new ConcurrentLinkedQueue<>();
        Random master = new Random(20240623L);

        for (int w = 0; w < workers; w++) {
            final int wid = w;
            final long workerSeed = master.nextLong();
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done.countDown();
                    return;
                }
                Random rnd = new Random(workerSeed);
                for (int i = 0; i < opsPerWorker; i++) {
                    results.add(performOp(modeFn.applyAsInt(i), wid, i, setup, rnd));
                }
                done.countDown();
            });
        }

        long t0 = System.nanoTime();
        start.countDown();
        boolean finished = done.await(15, TimeUnit.MINUTES);
        long wallNanos = System.nanoTime() - t0;
        pool.shutdownNow();
        assertTrue(finished, "load test workers did not complete within the timeout");

        return new RunResult(wallNanos / 1000, results);
    }

    private OpResult performOp(int mode, int wid, int i, Setup setup, Random rnd) {
        String op = opName(mode);
        long t0 = System.nanoTime();
        try {
            Response r;
            switch (mode) {
                case CREATE -> r = authed(setup.apiKey()).contentType(ContentType.JSON)
                    .body(createBody(setup.collectionId(), wid + "-" + i)).post("/bookmarks");
                case UPDATE -> r = authed(setup.apiKey()).contentType(ContentType.JSON)
                    .body(updateBody(setup.collectionId(), wid + "-" + i))
                    .put("/bookmarks/" + pick(setup.bookmarkIds(), rnd));
                case TRACK -> r = authed(setup.apiKey())
                    .post("/bookmarks/" + pick(setup.bookmarkIds(), rnd) + "/track-click");
                case BATCH_TAG -> r = authed(setup.apiKey()).contentType(ContentType.JSON)
                    .body(batchTagBody(setup.collectionId(), setup.tagId(), sublist(setup.bookmarkIds(), rnd)))
                    .post("/bookmarks/batch-tag");
                case BATCH_MOVE -> r = authed(setup.apiKey()).contentType(ContentType.JSON)
                    .body(batchMoveBody(setup.collectionId(), setup.folderId(), sublist(setup.bookmarkIds(), rnd)))
                    .post("/bookmarks/batch-move");
                default -> throw new IllegalStateException("mode " + mode);
            }
            long micros = (System.nanoTime() - t0) / 1000;
            int status = r.statusCode();
            return new OpResult(op, status, micros, status >= 400 ? snippet(r) : "");
        } catch (Exception e) {
            long micros = (System.nanoTime() - t0) / 1000;
            return new OpResult(op, -1, micros, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---- request builders ------------------------------------------------------

    private RequestSpecification authed(String apiKey) {
        return RestAssured.given().header("X-API-Key", apiKey);
    }

    private String createBody(String collectionId, String suffix) {
        return "{\"collectionId\":\"" + collectionId + "\",\"title\":\"c-" + suffix
            + "\",\"url\":\"https://example.com/c-" + suffix + "\"}";
    }

    private String updateBody(String collectionId, String suffix) {
        return "{\"collectionId\":\"" + collectionId + "\",\"title\":\"u-" + suffix
            + "\",\"url\":\"https://example.com/u-" + suffix + "\"}";
    }

    private String batchTagBody(String collectionId, String tagId, List<String> ids) {
        return "{\"collectionId\":\"" + collectionId + "\",\"addTagIds\":[\"" + tagId
            + "\"],\"removeTagIds\":[],\"bookmarkIds\":[" + joinIds(ids) + "]}";
    }

    private String batchMoveBody(String collectionId, String folderId, List<String> ids) {
        return "{\"collectionId\":\"" + collectionId + "\",\"folderId\":\"" + folderId
            + "\",\"bookmarkIds\":[" + joinIds(ids) + "]}";
    }

    private String joinIds(List<String> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
    }

    private String pick(List<String> ids, Random rnd) {
        return ids.get(rnd.nextInt(ids.size()));
    }

    private List<String> sublist(List<String> ids, Random rnd) {
        int n = Math.min(Math.min(batchSize, BATCH_MAX), ids.size());
        List<String> out = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            out.add(ids.get(rnd.nextInt(ids.size())));
        }
        return out;
    }

    private String snippet(Response r) {
        try {
            String body = r.body().asString();
            return body.length() > 120 ? body.substring(0, 120) : body;
        } catch (Exception e) {
            return "<no body>";
        }
    }

    private String opName(int mode) {
        return switch (mode) {
            case CREATE -> "create";
            case UPDATE -> "update";
            case TRACK -> "track";
            case BATCH_TAG -> "batch-tag";
            case BATCH_MOVE -> "batch-move";
            default -> "?";
        };
    }

    // ---- reporting / soft assertions -------------------------------------------

    private void report(String title, RunResult result) {
        ConcurrentLinkedQueue<OpResult> results = result.results();
        Map<String, List<OpResult>> byOp = results.stream().collect(Collectors.groupingBy(OpResult::op));
        double wallSec = result.wallMicros() / 1_000_000.0;
        long errors = results.stream().filter(r -> !r.success()).count();

        System.out.println();
        System.out.println("================ " + title + " ================");
        System.out.printf("config: workers=%d opsPerWorker=%d batchSize=%d seed=%d busy_timeout=%s journal=%s%n",
            workers, opsPerWorker, batchSize, seed,
            System.getenv().getOrDefault(LoadTestProfile.ENV_BUSY_TIMEOUT, "10000"),
            switch (String.valueOf(System.getenv(LoadTestProfile.ENV_WAL)).toLowerCase()) {
                case "immediate" -> "WAL+IMMEDIATE";
                case "true" -> "WAL";
                default -> "rollback-journal";
            });
        System.out.printf("wall=%.2fs totalOps=%d throughput=%.1f ops/s errors=%d (%.1f%%)%n",
            wallSec, results.size(), results.isEmpty() ? 0 : results.size() / wallSec,
            errors, results.isEmpty() ? 0 : 100.0 * errors / results.size());
        System.out.printf("%-12s %7s %7s %9s %9s %9s %9s %9s  %-16s%n",
            "op", "count", "errors", "min_us", "p50_us", "p95_us", "p99_us", "max_us", "err_statuses");
        byOp.forEach((op, list) -> {
            long[] lat = list.stream().mapToLong(OpResult::micros).sorted().toArray();
            long errs = list.stream().filter(r -> !r.success()).count();
            String statuses = list.stream().filter(r -> !r.success())
                .map(r -> Integer.toString(r.status())).distinct().collect(Collectors.joining(","));
            System.out.printf("%-12s %7d %7d %9d %9d %9d %9d %9d  %-16s%n",
                op, list.size(), errs, lat[0], pct(lat, .5), pct(lat, .95), pct(lat, .99),
                lat[lat.length - 1], statuses);
        });
        results.stream().filter(r -> r.status() >= 500).findFirst()
            .ifPresent(r -> System.out.println("sample 5xx: [" + r.status() + "] " + r.error()));
    }

    private long pct(long[] sortedAsc, double p) {
        if (sortedAsc.length == 0) {
            return -1;
        }
        int idx = (int) Math.ceil(p * sortedAsc.length) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= sortedAsc.length) {
            idx = sortedAsc.length - 1;
        }
        return sortedAsc[idx];
    }

    private void softAssert(RunResult result) {
        long errors = result.results().stream().filter(r -> !r.success()).count();
        if ("true".equalsIgnoreCase(System.getenv("LINKWEAVE_LOADTEST_STRICT"))) {
            assertEquals(0, errors,
                "STRICT mode: expected zero non-2xx responses after a mitigation, got " + errors);
        } else {
            System.out.println("INFO: set LINKWEAVE_LOADTEST_STRICT=true to fail on any non-2xx "
                + "(use after a WAL / single-writer-queue fix lands).");
        }
        assertTrue(result.results().size() == (long) workers * opsPerWorker,
            "not all ops were recorded (workers=" + workers + ", ops=" + opsPerWorker + ")");
    }

    private static int env(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
