package org.linkweave.api.autotag;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkweave.api.autotag.llm.FakeLlmTaggingClient;
import org.linkweave.api.autotag.llm.LlmTaggingClient;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;

/**
 * The regression test for UC-108 BR-108-3 and its first acceptance criterion:
 * with the model stubbed to hang, saving a bookmark stays fast and no pull is
 * issued.
 *
 * <p>This is the incident of 2026-08-28 reproduced. The suggestion endpoint
 * inherited {@code @Transactional(REQUIRED)} from the {@code @JaxResource}
 * stereotype, so its authorization check acquired a pooled JDBC connection
 * before the model was called. The service being {@code NOT_SUPPORTED} only
 * suspended that transaction, and a suspended transaction keeps its connection —
 * Agroal returns it on completion, not on suspension. Every in-flight suggestion
 * therefore held a connection for the model's full read-timeout (30 s at the
 * time), and because auto-fire sent one for every bookmark being typed, the pool
 * drained and unrelated requests blocked in {@code getConnection()}. Users saw
 * the save button hang while tag suggestions were still in flight.
 *
 * <p>The pool is shrunk to two connections here ({@link StalledModelTestProfile})
 * so four hung suggestions are enough to prove it. Before the fix this test
 * fails by timing out on the save; after it, the save is served while all four
 * suggestions are still stuck inside the model client.
 */
@QuarkusTest
@TestProfile(StalledModelTestProfile.class)
class BookmarkAutoTagStallITest {

    /** Comfortably more than the pool, so a leaked connection per call would starve it. */
    private static final int CONCURRENT_SUGGESTIONS = 4;

    /** A save that takes longer than this is not "unaffected by model health". */
    private static final Duration SAVE_BUDGET = Duration.ofSeconds(10);

    @Inject
    FixtureService fixtureService;

    private final FakeLlmTaggingClient fake = new FakeLlmTaggingClient();
    private final CountDownLatch modelStall = new CountDownLatch(1);
    private ExecutorService callers;

    @BeforeEach
    void installStalledModel() {
        fake.reset();
        fake.stallUntil = modelStall;
        fake.namesToReturn = List.of("rust");
        QuarkusMock.installMockForType(fake, LlmTaggingClient.class);
        callers = Executors.newFixedThreadPool(CONCURRENT_SUGGESTIONS);
    }

    @AfterEach
    void releaseStalledModel() {
        modelStall.countDown();
        callers.shutdownNow();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldSaveBookmarkAtNormalSpeedWhileSuggestionsAreStalled() throws Exception {
        // ARRANGE — a collection with a vocabulary, so suggestions actually reach
        // the (hanging) model rather than short-circuiting on an empty one.
        Collection collection = fixtureService.createTestCollection();
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));
        UUID collectionId = collection.getId().getUUID();

        for (int i = 0; i < CONCURRENT_SUGGESTIONS; i++) {
            callers.submit(() -> RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                    {"title":"Async Rust","url":"https://example.com/rust"}
                    """)
                .post("/collections/{cid}/autotag/suggest-tags", collectionId));
        }
        Assertions.assertThat(awaitStalledCallers())
            .as("all %s suggestions are stuck inside the model client", CONCURRENT_SUGGESTIONS)
            .isEqualTo(CONCURRENT_SUGGESTIONS);

        // ACT — save a bookmark while every one of them is still hanging.
        long startedAt = System.nanoTime();
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","title":"Saved during a stall","url":"https://example.com/saved"}
                """.formatted(collectionId))
            .post("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200);
        Duration saveTook = Duration.ofNanos(System.nanoTime() - startedAt);

        Assertions.assertThat(saveTook)
            .as("bookmark save latency is independent of model health (BR-108-3)")
            .isLessThan(SAVE_BUDGET);
        Assertions.assertThat(fake.inFlight.get())
            .as("the suggestions really were still stalled when the save was served")
            .isEqualTo(CONCURRENT_SUGGESTIONS);
    }

    /** Waits until every caller is parked inside the model client, or gives up. */
    private int awaitStalledCallers() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline && fake.inFlight.get() < CONCURRENT_SUGGESTIONS) {
            Thread.sleep(25);
        }
        return fake.inFlight.get();
    }
}
