package org.linkweave.api.collection.events;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Does a real client hanging up actually free what it was holding?
 *
 * <p>The unit tests cancel subscriptions programmatically, which proves the
 * release logic but not the thing that matters in production: that a browser
 * closing a tab, or a laptop going to sleep, reaches that logic at all. Between
 * the socket and {@code onTermination} sit Vert.x, RESTEasy's SSE writer and the
 * merge with the heartbeat — any of which could swallow the cancellation and
 * leave a sink, a {@code BroadcastProcessor} and a periodic timer alive for the
 * lifetime of the process, one per abandoned tab.
 *
 * <p>So this drives the endpoint over real HTTP and hangs up.
 */
@QuarkusTest
class CollectionEventDisconnectITest {

    private static final Duration RELEASE_TIMEOUT = Duration.ofSeconds(10);

    @Inject
    CollectionEventBroadcaster broadcaster;

    @Inject
    FixtureService fixtureService;

    private URI streamUri(Collection collection) {
        return URI.create("http://localhost:" + RestAssured.port
            + "/api/collections/" + collection.getId().getUUID() + "/events?clientId=disconnect-probe");
    }

    /** Polls, because the release happens on the server's own threads. */
    private void awaitOpenCollectionCount(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + RELEASE_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (broadcaster.openCollectionCount() == expected) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertThat(broadcaster.openCollectionCount())
            .as("sink count did not settle within %s", RELEASE_TIMEOUT)
            .isEqualTo(expected);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReleaseTheSinkWhenTheClientHangsUp() throws Exception {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        int before = broadcaster.openCollectionCount();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<Stream<String>> response = client.send(
            HttpRequest.newBuilder(streamUri(collection)).GET().build(),
            HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);

        // The opening heartbeat proves the stream is live and subscribed, so the
        // release afterwards is measuring something real rather than a subscribe
        // that never happened.
        try (Stream<String> lines = response.body()) {
            Iterator<String> frames = lines.iterator();
            assertThat(frames.hasNext()).isTrue();
            assertThat(frames.next()).contains("data:");
            assertThat(broadcaster.openCollectionCount())
                .as("the live stream holds exactly one sink")
                .isEqualTo(before + 1);

            // ACT — hang up mid-stream, the way a closed tab does
        }

        // ASSERT
        awaitOpenCollectionCount(before);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotAccumulateSinksAcrossManyConnectAndDisconnectCycles() throws Exception {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        int before = broadcaster.openCollectionCount();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        // ACT — a tab that reconnects repeatedly, e.g. a flaky network
        for (int i = 0; i < 5; i++) {
            HttpResponse<Stream<String>> response = client.send(
                HttpRequest.newBuilder(streamUri(collection)).GET().build(),
                HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                assertThat(lines.iterator().hasNext()).isTrue();
            }
        }

        // ASSERT — the map is back where it started, not five entries deeper
        awaitOpenCollectionCount(before);
    }
}
