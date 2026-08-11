package org.linkweave.api.collection.events;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import io.smallrye.mutiny.Multi;
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

    /**
     * The heartbeat's periodic tick is a Vert.x timer (Quarkus swaps Mutiny's
     * scheduler for {@code VertxTimerAwareScheduledExecutorService}), and one
     * that outlived its subscriber would be a leak per abandoned tab that the
     * broadcaster's sink count cannot see.
     *
     * <p>Mutiny cancels the timer itself: {@code IntervalMulti}'s subscription
     * calls {@code future.cancel(false)}. What that guarantee depends on — and
     * what this composition owns — is the cancel signal actually reaching the
     * branch, since the client subscribes to a <em>merge</em> and not to the
     * ticker directly. So the assertion is on propagation, measured at a source
     * that can report it: instrumenting the heartbeat branch from outside would
     * only prove a cancelled subscriber stops receiving items, which is true of
     * any cancelled stream and would pass even against a leaking timer.
     */
    @Test
    void shouldCancelEveryBranchOfTheMergeWhenTheClientLeaves() {
        // ARRANGE — the resource's shape: a source branch merged with heartbeats
        ID<Collection> collectionId = ID.random(Collection.class);
        AtomicBoolean sourceCancelled = new AtomicBoolean();
        Multi<CollectionEventJson> source = Multi.createFrom()
            .<CollectionEventJson>emitter(emitter -> emitter.onTermination(() -> sourceCancelled.set(true)));
        Multi<CollectionEventJson> merged = Multi.createBy().merging().streams(
            source,
            CollectionEventResource.heartbeats(collectionId, Duration.ofMillis(10)));
        AssertSubscriber<CollectionEventJson> subscriber =
            merged.subscribe().withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));
        subscriber.awaitItems(1);

        // ACT — the ordinary "client hung up" case
        subscriber.cancel();

        // ASSERT
        assertThat(sourceCancelled)
            .as("cancelling the merged stream must reach the branches, not just the merge")
            .isTrue();
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
