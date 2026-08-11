package org.linkweave.api.collection.events;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.types.id.ID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the fan-out semantics UC-104 depends on. No Quarkus needed —
 * the broadcaster holds nothing but a map of hot streams.
 */
class CollectionEventBroadcasterTest {

    private final CollectionEventBroadcaster broadcaster = new CollectionEventBroadcaster();

    private static CollectionEventJson event(ID<Collection> collectionId, @Nullable String originClientId) {
        return new CollectionEventJson(
            collectionId, ID.random(Bookmark.class),
            ChangeKind.SCREENSHOT_READY, originClientId, null);
    }

    private static AssertSubscriber<CollectionEventJson> listen(Multi<CollectionEventJson> stream) {
        return stream.subscribe().withSubscriber(AssertSubscriber.create(10));
    }

    @Test
    void shouldFanOutToEverySubscriberOfTheCollection() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);
        AssertSubscriber<CollectionEventJson> first = listen(broadcaster.subscribe(collectionId, null));
        AssertSubscriber<CollectionEventJson> second = listen(broadcaster.subscribe(collectionId, null));

        // ACT
        broadcaster.publish(event(collectionId, null));

        // ASSERT
        assertThat(first.getItems()).hasSize(1);
        assertThat(second.getItems()).hasSize(1);
    }

    @Test
    void shouldNotDeliverToSubscribersOfAnotherCollection() {
        // ARRANGE
        ID<Collection> watched = ID.random(Collection.class);
        ID<Collection> other = ID.random(Collection.class);
        AssertSubscriber<CollectionEventJson> subscriber = listen(broadcaster.subscribe(watched, null));

        // ACT
        broadcaster.publish(event(other, null));

        // ASSERT — BR-210: notifications never leak across collections
        subscriber.assertHasNotReceivedAnyItem();
    }

    @Test
    void shouldNotReplayEarlierEventsToALateSubscriber() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);
        listen(broadcaster.subscribe(collectionId, null));
        broadcaster.publish(event(collectionId, null));

        // ACT
        AssertSubscriber<CollectionEventJson> late = listen(broadcaster.subscribe(collectionId, null));

        // ASSERT — BR-204: no queue, no retained history, no replay
        late.assertHasNotReceivedAnyItem();
    }

    @Test
    void shouldIgnorePublishWhenNobodyIsListening() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);

        // ACT / ASSERT — a notification for an unwatched collection is dropped, not queued
        broadcaster.publish(event(collectionId, null));

        assertThat(broadcaster.openCollectionCount()).isZero();
    }

    @Test
    void shouldNotDeliverAClientItsOwnEvent() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);
        AssertSubscriber<CollectionEventJson> tab = listen(broadcaster.subscribe(collectionId, "tab-1"));

        // ACT
        broadcaster.publish(event(collectionId, "tab-1"));

        // ASSERT — BR-205: the tab that caused the change already has the result
        tab.assertHasNotReceivedAnyItem();
    }

    @Test
    void shouldDeliverEventsCausedByAnotherTabOfTheSameUser() {
        // ARRANGE — the trap the tab-scoped id exists to avoid: filtering on the
        // user instead would make this second tab discard updates it needs.
        ID<Collection> collectionId = ID.random(Collection.class);
        AssertSubscriber<CollectionEventJson> secondTab = listen(broadcaster.subscribe(collectionId, "tab-2"));

        // ACT
        broadcaster.publish(event(collectionId, "tab-1"));

        // ASSERT
        assertThat(secondTab.getItems()).hasSize(1);
    }

    @Test
    void shouldDeliverBackgroundJobEventsToEveryTab() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);
        AssertSubscriber<CollectionEventJson> tab = listen(broadcaster.subscribe(collectionId, "tab-1"));

        // ACT — a scheduled job has no originating tab (UC-104 A1)
        broadcaster.publish(event(collectionId, null));

        // ASSERT
        assertThat(tab.getItems()).hasSize(1);
    }

    @Test
    void shouldDeliverEverythingToASubscriberThatIdentifiesNoTab() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);
        AssertSubscriber<CollectionEventJson> anonymous = listen(broadcaster.subscribe(collectionId, null));

        // ACT
        broadcaster.publish(event(collectionId, "tab-1"));

        // ASSERT
        assertThat(anonymous.getItems()).hasSize(1);
    }

    @Test
    void shouldReleaseTheSinkWhenTheLastSubscriberLeaves() {
        // ARRANGE
        ID<Collection> collectionId = ID.random(Collection.class);
        AssertSubscriber<CollectionEventJson> first = listen(broadcaster.subscribe(collectionId, null));
        AssertSubscriber<CollectionEventJson> second = listen(broadcaster.subscribe(collectionId, null));

        // ACT
        first.cancel();

        // ASSERT — one subscriber left, sink stays
        assertThat(broadcaster.openCollectionCount())
            .as("sink retained while a subscriber remains")
            .isEqualTo(1);

        second.cancel();
        assertThat(broadcaster.openCollectionCount())
            .as("sink released once the last subscriber goes away, so the map cannot grow unbounded")
            .isZero();
    }

    @Test
    void shouldNotReserveASinkForAStreamNobodySubscribedTo() {
        // ARRANGE / ACT — e.g. the client disconnects before the response is wired up
        ID<Collection> collectionId = ID.random(Collection.class);
        broadcaster.subscribe(collectionId, null);

        // ASSERT — nothing to release later, so nothing may be held
        assertThat(broadcaster.openCollectionCount())
            .as("a stream that was never subscribed to must not pin a sink forever")
            .isZero();
    }

    @Test
    void shouldTrackEachSubscriptionOfTheSameStreamSeparately() {
        // ARRANGE — one Multi, two subscriptions: releasing must be per subscription,
        // or the surviving one ends up attached to a sink nobody publishes to.
        ID<Collection> collectionId = ID.random(Collection.class);
        Multi<CollectionEventJson> stream = broadcaster.subscribe(collectionId, null);
        AssertSubscriber<CollectionEventJson> first = listen(stream);
        AssertSubscriber<CollectionEventJson> second = listen(stream);

        // ACT
        first.cancel();
        broadcaster.publish(event(collectionId, null));

        // ASSERT
        assertThat(broadcaster.openCollectionCount()).isEqualTo(1);
        assertThat(second.getItems())
            .as("the remaining subscription is still connected to the live sink")
            .hasSize(1);
    }
}
