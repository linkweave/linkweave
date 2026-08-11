package org.linkweave.api.collection.events;

import java.time.Duration;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-201 — a client may only listen to a collection it currently has access to,
 * checked at subscribe through the same authorization path as every other
 * collection operation.
 *
 * <p>Only the rejection paths are driven over HTTP: a successful subscribe opens a
 * stream that never completes, which RestAssured cannot assert against without
 * hanging. The accepted path is therefore exercised by calling the resource
 * through CDI and consuming the returned {@link Multi} directly — enough to run
 * the authorization check, the stream assembly and the teardown, none of which is
 * reached by a request that is rejected. Serialization and reconnect behaviour on
 * the wire remain uncovered until the frontend client exists (UC-104 phase 1 has
 * no consumer of this endpoint yet), which is where an e2e test can assert a
 * preview appearing without a reload.
 */
@QuarkusTest
class CollectionEventResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    CollectionEventResource resource;

    @Inject
    CollectionEventBroadcaster broadcaster;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldStreamEventsOfTheSubscribedCollectionAndReleaseTheSinkOnDisconnect() {
        // ARRANGE — the broadcaster is @ApplicationScoped and therefore shared by
        // every test in this Quarkus instance, so sink counts are asserted relative
        // to a baseline rather than absolutely.
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = fixtureService.persistBookmark(b -> b.withCollection(collection));
        int sinksBefore = broadcaster.openCollectionCount();
        AssertSubscriber<CollectionEventJson> subscriber = resource.stream(collection.getId(), "tab-1")
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        // ACT — one event from another tab, one this subscriber caused itself
        broadcaster.publish(event(collection, bookmark, "tab-2"));
        broadcaster.publish(event(collection, bookmark, "tab-1"));

        // ASSERT — the merged stream carries both branches: the opening heartbeat
        // (which completes the handshake) and real events, with BR-205 still
        // filtering through the merge. A heartbeat branch that swallowed or
        // duplicated items, or a filter applied to the wrong branch, shows up here.
        //
        // Deliberately no assertion on the order of the two: the heartbeat is
        // emitted on Mutiny's worker pool and the events on the caller's thread, so
        // a merge interleaves them nondeterministically — pinning an order here
        // would be pinning a race, not a contract.
        subscriber.awaitItems(2, Duration.ofSeconds(5));
        assertThat(subscriber.getItems())
            .filteredOn(e -> e.getKind() == ChangeKind.SCREENSHOT_READY)
            .extracting(CollectionEventJson::getOriginClientId)
            .as("the other tab's event passes; this tab's own event does not")
            .containsExactly("tab-2");
        assertThat(subscriber.getItems())
            .anyMatch(e -> e.getKind() == ChangeKind.HEARTBEAT);
        assertThat(broadcaster.openCollectionCount()).isEqualTo(sinksBefore + 1);

        // ACT — the ordinary "user closed the tab" case
        subscriber.cancel();

        // ASSERT — cancellation reaches the broadcaster through the merge
        assertThat(broadcaster.openCollectionCount())
            .as("merging in the heartbeat must not keep the sink alive after the client leaves")
            .isEqualTo(sinksBefore);
    }

    private static CollectionEventJson event(Collection collection, Bookmark bookmark, String originClientId) {
        return new CollectionEventJson(
            collection.getId(), bookmark.getId(), ChangeKind.SCREENSHOT_READY, originClientId, null);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRejectSubscribingToACollectionTheUserCannotAccess() {
        // ARRANGE — a collection belonging to somebody else
        // Unique per run: the test database is reused across executions, so a
        // fixed address trips User.email's unique constraint on the second run.
        User stranger = fixtureService.persistUser(b -> b
            .withEmail("stranger-" + UUID.randomUUID() + "@example.com"));
        Collection foreign = fixtureService.persistCollection(b -> b
            .withName("Not yours")
            .withOwner(stranger));

        // ACT
        RestAssured.given()
            .queryParam("clientId", "tab-1")
            .get("/collections/{collectionId}/events", foreign.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    void shouldRejectAnUnauthenticatedSubscribe() {
        // ARRANGE
        UUID unknownCollection = UUID.randomUUID();

        // ACT
        RestAssured.given()
            .queryParam("clientId", "tab-1")
            .get("/collections/{collectionId}/events", unknownCollection)
            // ASSERT
            .then()
            .statusCode(401);
    }
}
