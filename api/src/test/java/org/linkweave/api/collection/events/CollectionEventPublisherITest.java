package org.linkweave.api.collection.events;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.id.ID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BR-203 — "notify only after the change is durable".
 *
 * <p>The rollback case is the reason this test exists. A notification for a write
 * that never landed makes every client reload and find nothing, or worse, trust a
 * state the database rejected. Because {@link CollectionEventPublisher} observes
 * {@code AFTER_SUCCESS}, the container enforces this rather than the calling code
 * — and a regression here (someone publishing straight to the broadcaster) would
 * be invisible on the happy path.
 */
@QuarkusTest
class CollectionEventPublisherITest {

    @Inject
    CollectionEventBroadcaster broadcaster;

    @Inject
    FixtureService fixtureService;

    @Inject
    TransactionalFirerTestHelper firer;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldPublishOnceTheTransactionCommits() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = fixtureService.persistBookmark(b -> b.withCollection(collection));
        AssertSubscriber<CollectionEventJson> subscriber = broadcaster.subscribe(collection.getId(), null)
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        // ACT
        firer.fireAndCommit(collection.getId(), bookmark.getId());

        // ASSERT
        assertThat(subscriber.getItems()).hasSize(1);
        assertThat(subscriber.getItems().getFirst().getKind()).isEqualTo(ChangeKind.SCREENSHOT_READY);
        assertThat(subscriber.getItems().getFirst().getCollectionId()).isEqualTo(collection.getId());

        // The broadcaster is @ApplicationScoped: a subscription left open here
        // outlives the test and skews sink counts in every test that follows.
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldPublishNothingWhenTheTransactionRollsBack() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = fixtureService.persistBookmark(b -> b.withCollection(collection));
        AssertSubscriber<CollectionEventJson> subscriber = broadcaster.subscribe(collection.getId(), null)
            .subscribe().withSubscriber(AssertSubscriber.create(10));
        ID<Collection> collectionId = collection.getId();
        ID<Bookmark> bookmarkId = bookmark.getId();

        // ACT
        assertThatThrownBy(() -> firer.fireAndRollback(collectionId, bookmarkId))
            .isInstanceOf(IllegalStateException.class);

        // ASSERT — the write never landed, so no client may be told it did
        subscriber.assertHasNotReceivedAnyItem();
        subscriber.cancel();
    }

    /**
     * Fires {@link CollectionChanged} from inside a real transaction so the
     * observer's {@code AFTER_SUCCESS} phase is exercised for both outcomes.
     */
    @ApplicationScoped
    @RequiredArgsConstructor
    static class TransactionalFirerTestHelper {

        private final Event<CollectionChanged> collectionChanged;

        @Transactional
        void fireAndCommit(ID<Collection> collectionId, ID<Bookmark> bookmarkId) {
            collectionChanged.fire(new CollectionChanged(
                collectionId, bookmarkId, ChangeKind.SCREENSHOT_READY, null, null));
        }

        @Transactional
        void fireAndRollback(ID<Collection> collectionId, ID<Bookmark> bookmarkId) {
            collectionChanged.fire(new CollectionChanged(
                collectionId, bookmarkId, ChangeKind.SCREENSHOT_READY, null, null));
            throw new IllegalStateException("forced rollback");
        }
    }
}
