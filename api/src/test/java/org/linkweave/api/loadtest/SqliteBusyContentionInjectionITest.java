package org.linkweave.api.loadtest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.linkweave.infrastructure.db.DatabaseService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Measures what a real request does when it meets real lock contention (UC-109, NFR-030).
 *
 * <h2>Why not the load test</h2>
 * {@code SqliteWriteContentionLoadITest} produces contention <em>statistically</em>: many writers,
 * and some fraction of requests lose the race. That is the right tool for measuring latency and
 * error rates, and the wrong one for proving a specific rule, because the interesting request is
 * whichever one happened to collide. This injects the lock instead, so every run exercises the same
 * path: a second connection takes the database in {@code locking_mode=EXCLUSIVE}, which blocks
 * every other connection — readers included — for a known window.
 *
 * <h2>What each case claims</h2>
 * <ul>
 *   <li><b>Brief contention on the identity lookup</b> — the lock clears inside the retry budget,
 *       so BR-109-3 should absorb it and the caller should never see an error at all.</li>
 *   <li><b>Sustained contention</b> — the retry budget is exhausted on purpose, so the request
 *       cannot succeed. BR-109-1 then says the answer is 503 with {@code Retry-After}: the caller
 *       is told to come back, not that the server is broken. A 500 here is the incident.</li>
 *   <li>Both are asserted on the identity lookup <em>and</em> on an ordinary write, because they
 *       fail through different machinery — the write through a JAX-RS exception mapper, the
 *       identity lookup through the HTTP authentication layer, which runs before JAX-RS.</li>
 * </ul>
 *
 * <h2>What runs in CI</h2>
 * The sustained-contention cases do, and deliberately so: they are the only automated proof that a
 * contended request reaches the caller as 503 rather than 500, and for the identity lookup that
 * answer travels from the Vert.x authentication layer into a JAX-RS exception mapper — framework
 * routing that a Quarkus upgrade could quietly change while every other test here stayed green.
 * They are deterministic (a lock is held for a fixed window, and the premise is asserted before
 * each measurement) and they run against their own database file, so nothing leaks into the rest
 * of the suite.
 *
 * <p>Only {@link #shouldSurviveBriefContentionOnTheIdentityLookup()} is gated, because it races a
 * 25&nbsp;ms lock against a retry schedule and that is not a race to run on a shared CI box:
 * <pre>
 * ./mvnw test -pl api -Dtest=SqliteBusyContentionInjectionITest -DLINKWEAVE_LOADTEST=true
 * </pre>
 */
@QuarkusTest
@TestProfile(ContentionInjectionProfile.class)
class SqliteBusyContentionInjectionITest {

    static boolean loadTestGateEnabled() {
        return "true".equalsIgnoreCase(LoadTestProfile.setting(LoadTestProfile.ENV_ENABLED));
    }

    /** Shorter than the identity lookup's retry budget (~70 ms), so retrying can win. */
    private static final long BRIEF_HOLD_MS = 25;

    /**
     * Longer than any retry budget in the application, so no amount of retrying can win.
     *
     * <p>The longest budget is {@code BookmarkResource}'s {@code @RetryOnSqliteBusy(attempts =
     * 12)}: eleven delays that the interceptor's capped-exponential schedule stretches to at most
     * 10+20+40+80+160+250&times;6 &asymp; 1.81&nbsp;s, so the final attempt can start that late.
     * The margin, not the mean, is what matters here — a hold only a little longer than 1.81&nbsp;s
     * lets a {@code Thread.sleep} that overshoots on a loaded CI box run the last attempt after the
     * lock cleared, and the request would then <em>succeed</em> and fail the 503 assertion in the
     * case this class calls deterministic.</p>
     *
     * <p>The headroom is free: {@code release.countDown()} in {@link #underExclusiveLock}'s
     * {@code finally} drops the lock as soon as the action returns, so this is a ceiling on the
     * hold and not a sleep the suite pays for.</p>
     */
    private static final long SUSTAINED_HOLD_MS = 10_000;

    private static final int BRIEF_ITERATIONS = 20;

    /** Must name the same file as {@link ContentionInjectionProfile}, or nothing is contended. */
    private static final String JDBC_URL = "jdbc:sqlite:linkweave-contention-test.db";

    @Inject
    DatabaseService databaseService;

    @Inject
    EntityManager em;

    private String sessionCookie;
    private String collectionId;

    @BeforeEach
    void logIn() {
        databaseService.resetDatabase();

        // Null under @TestSecurity, which short-circuits form authentication and issues no
        // cookie. The two cases that need a real identity lookup assert on it themselves.
        sessionCookie = RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "test@example.com")
            .formParam("j_password", "test")
            .post("/j_security_check")
            .then().statusCode(200)
            .extract().cookie("linkweave-credential");

        // The first /auth/me provisions the default collection; a write needs somewhere to write to.
        authed().get("/auth/me").then().statusCode(200);
        collectionId = authed().get("/collections").then().statusCode(200).extract().path("collections[0].id");
        assertNotNull(collectionId, "the logged-in user must own a collection");

        // Self-verifying, like the load test's runtimeDiagnostics(): a wrong busy_timeout silently
        // turns every result in this class into "waited, then succeeded".
        System.out.printf("runtime: PRAGMA journal_mode=%s PRAGMA busy_timeout=%s%n",
            em.createNativeQuery("PRAGMA journal_mode").getSingleResult(),
            em.createNativeQuery("PRAGMA busy_timeout").getSingleResult());
    }

    @Test
    @EnabledIf("loadTestGateEnabled")
    void shouldSurviveBriefContentionOnTheIdentityLookup() {
        requireRealSessionAuth();
        Map<Integer, Integer> statuses = new LinkedHashMap<>();
        for (int i = 0; i < BRIEF_ITERATIONS; i++) {
            int status = underExclusiveLock(BRIEF_HOLD_MS,
                () -> authed().get("/auth/me").statusCode());
            statuses.merge(status, 1, Integer::sum);
        }

        report("BR-109-3: identity lookup, lock held " + BRIEF_HOLD_MS + "ms (inside retry budget)",
            statuses);
        assertEquals(0, statuses.getOrDefault(500, 0),
            "contention must never reach the caller as a 500: " + statuses);
        assertEquals(BRIEF_ITERATIONS, statuses.getOrDefault(200, 0),
            "brief contention on the identity lookup must be absorbed by the retry: " + statuses);
    }

    @Test
    void shouldAnswer503WhenContentionOutlastsTheRetryBudgetOnTheIdentityLookup() {
        requireRealSessionAuth();
        Response response = underExclusiveLock(SUSTAINED_HOLD_MS, () -> authed().get("/auth/me"));

        report("BR-109-1: identity lookup, lock held " + SUSTAINED_HOLD_MS + "ms (beyond any budget)",
            Map.of(response.statusCode(), 1));
        assertEquals(503, response.statusCode(),
            "exhausted contention must be a retryable 503, not a crash: " + body(response));
        assertNotNull(response.header("Retry-After"), "503 must tell the caller when to come back");
    }

    @Test
    void shouldAnswer503WhenContentionOutlastsTheRetryBudgetOnAnApiKeyRequest() {
        // ARRANGE — the CLI and extension authenticate this way, through our own
        // ApiKeyIdentityProvider rather than the generated one, so it is a separate path with the
        // same exposure: a lookup in front of every request.
        requireRealSessionAuth();
        String apiKey = authed()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"ContentionKey\"}")
            .post("/auth/api-keys")
            .then().statusCode(201)
            .extract().path("key");

        // ACT
        Response response = underExclusiveLock(SUSTAINED_HOLD_MS,
            () -> RestAssured.given().header("X-API-Key", apiKey).get("/collections"));

        // ASSERT
        report("BR-109-1: API-key lookup, lock held " + SUSTAINED_HOLD_MS + "ms",
            Map.of(response.statusCode(), 1));
        assertEquals(503, response.statusCode(),
            "API-key authentication must answer 503 under contention too: " + body(response));
        assertNotNull(response.header("Retry-After"), "503 must tell the caller when to come back");
    }

    /**
     * Isolates the write path. {@code @TestSecurity} is used here <em>because</em> it bypasses the
     * identity lookup: with a real session cookie the lookup is contended too and answers 503
     * first, so the write would never be reached and this case would silently re-measure the
     * previous one.
     */
    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE", "BOOKMARK_READ"})
    void shouldAnswer503WhenContentionOutlastsTheRetryBudgetOnAWrite() {
        Response response = underExclusiveLock(SUSTAINED_HOLD_MS, () -> RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"collectionId\":\"" + collectionId + "\",\"title\":\"contended\","
                + "\"url\":\"https://example.com/contended\"}")
            .post("/bookmarks"));

        report("BR-109-1: bookmark write, lock held " + SUSTAINED_HOLD_MS + "ms",
            Map.of(response.statusCode(), 1));
        assertEquals(503, response.statusCode(),
            "exhausted contention must be a retryable 503, not a crash: " + body(response));
        assertNotNull(response.header("Retry-After"), "503 must tell the caller when to come back");
    }

    // ---- contention injection --------------------------------------------------

    /**
     * Runs {@code action} while a second connection holds the database exclusively.
     *
     * <p>{@code locking_mode=EXCLUSIVE} is what makes this deterministic. An ordinary write
     * transaction only takes a RESERVED lock, which still lets other connections read — so it
     * could not block an identity lookup. In exclusive locking mode the connection keeps the
     * database-level lock from its first write until it is released, and every other connection
     * gets {@code SQLITE_BUSY} immediately (the datasource runs with {@code busy_timeout=1}, and
     * {@link ContentionInjectionProfile} explains why not 0).</p>
     *
     * <p>The action only starts once the lock is confirmed held, so the test never measures a
     * request that raced ahead of the contention.</p>
     */
    private <T> T underExclusiveLock(long holdMillis, Supplier<T> action) {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Exception> holderFailure = new AtomicReference<>();
        Thread holder = new Thread(
            () -> holdExclusively(locked, release, holdMillis, holderFailure), "lock-holder");
        holder.setDaemon(true);
        holder.start();
        try {
            assertTrue(locked.await(30, TimeUnit.SECONDS), "the lock holder never reported back");
            assertNull(holderFailure.get(), "could not acquire the exclusive lock: "
                + (holderFailure.get() == null ? "" : holderFailure.get().getMessage()));
            assertInjectedLockBlocksOtherConnections();
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            release.countDown();
            try {
                holder.join(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void holdExclusively(CountDownLatch locked, CountDownLatch release, long holdMillis,
        AtomicReference<Exception> failure) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL);
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            // BEGIN EXCLUSIVE, not an ordinary write transaction: a write first takes only a
            // RESERVED lock, which blocks other writers but still lets readers in — so it could
            // never block an identity lookup. EXCLUSIVE is acquired up front and blocks everyone.
            stmt.execute("BEGIN EXCLUSIVE");
            locked.countDown();
            release.await(holdMillis, TimeUnit.MILLISECONDS);
            stmt.execute("ROLLBACK");
        } catch (SQLException | InterruptedException e) {
            failure.set(e);
            locked.countDown();
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Proves the premise before every measurement: an unrelated connection must now be unable to
     * read. Without this the whole class degrades silently into "nothing was contended after all",
     * which is exactly what the first run of it did.
     */
    private void assertInjectedLockBlocksOtherConnections() {
        try (Connection probe = DriverManager.getConnection(JDBC_URL + "?busy_timeout=1");
             Statement stmt = probe.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM User")) {
            rs.next();
            fail("the injected lock is not blocking other connections, so nothing below measures "
                + "contention (read returned " + rs.getInt(1) + " rows)");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().contains("SQLITE_BUSY")
                    || expected.getMessage().contains("database is locked"),
                "expected the probe read to be refused with SQLITE_BUSY, got: "
                    + expected.getMessage());
        }
    }

    // ---- helpers ---------------------------------------------------------------

    private io.restassured.specification.RequestSpecification authed() {
        return sessionCookie == null
            ? RestAssured.given()
            : RestAssured.given().cookie("linkweave-credential", sessionCookie);
    }

    /** The identity-lookup cases are meaningless unless the lookup is really happening. */
    private void requireRealSessionAuth() {
        assertNotNull(sessionCookie, "this case must authenticate through the real identity "
            + "lookup, so a form-login cookie is required");
    }

    private String body(Response response) {
        try {
            return response.body().asString();
        } catch (Exception e) {
            return "<no body>";
        }
    }

    private void report(String title, Map<Integer, Integer> statuses) {
        System.out.println();
        System.out.println("================ " + title + " ================");
        statuses.forEach((status, count) -> System.out.printf("  HTTP %d : %d%n", status, count));
    }
}
