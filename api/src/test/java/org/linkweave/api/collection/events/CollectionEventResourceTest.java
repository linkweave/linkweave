package org.linkweave.api.collection.events;

import java.time.Duration;

import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.types.id.ID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-208 — the stream keeps itself alive while idle.
 *
 * <p>An idle stream is this channel's normal state, so without heartbeats an
 * intermediary that closes idle connections would drop it with no symptom on
 * either end. The interval used in production is far too long for a test, hence
 * the package-private seam taking one.
 */
class CollectionEventResourceTest {

    @Test
    void shouldOpenWithAnImmediateHeartbeatRatherThanWaitingOutTheFirstInterval() {
        // ARRANGE — an interval far longer than the test could ever wait for, so
        // this passes only if the opening frame is emitted on subscribe. That
        // frame is what completes the browser's SSE handshake.
        ID<Collection> collectionId = ID.random(Collection.class);

        // ACT
        AssertSubscriber<CollectionEventJson> subscriber =
            CollectionEventResource.heartbeats(collectionId, Duration.ofMinutes(10))
                .subscribe().withSubscriber(AssertSubscriber.create(1));

        // ASSERT
        subscriber.awaitItems(1, Duration.ofSeconds(5));
        assertThat(subscriber.getItems().getFirst().getKind()).isEqualTo(ChangeKind.HEARTBEAT);
    }

    @Test
    void shouldKeepEmittingHeartbeatsWhileTheStreamStaysIdle() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);

        // ACT
        AssertSubscriber<CollectionEventJson> subscriber =
            CollectionEventResource.heartbeats(collectionId, Duration.ofMillis(10))
                .subscribe().withSubscriber(AssertSubscriber.create(3));

        // ASSERT
        subscriber.awaitItems(3);
        CollectionEventJson heartbeat = subscriber.getItems().getFirst();
        assertThat(heartbeat.getKind()).isEqualTo(ChangeKind.HEARTBEAT);
        assertThat(heartbeat.getCollectionId()).isEqualTo(collectionId);
        assertThat(heartbeat.getBookmarkId())
            .as("a heartbeat announces no change, so it names no bookmark")
            .isNull();
        assertThat(heartbeat.getOriginClientId())
            .as("belongs to no tab, so it survives the BR-205 filter for every client")
            .isNull();
    }
}
