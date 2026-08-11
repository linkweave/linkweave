package org.linkweave.api.collection.events;

import java.util.Set;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.bookmark.json.BookmarkSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-104 main scenario — a member's change reaches the other members' clients.
 *
 * <p>Driven through {@link BookmarkService} rather than the notifier directly:
 * what is worth protecting is that the write paths actually announce themselves,
 * which a test of the notifier in isolation would happily pass while every
 * caller stayed silent.
 */
@QuarkusTest
class CollectionChangeNotificationServiceITest {

    @Inject
    CollectionEventBroadcaster broadcaster;

    @Inject
    BookmarkService bookmarkService;

    @Inject
    FixtureService fixtureService;

    private Bookmark persistBookmark(Collection collection, String title) {
        return fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle(title)
            .withUrl("https://example.com/" + title));
    }

    private AssertSubscriber<CollectionEventJson> listen(Collection collection) {
        return broadcaster.subscribe(collection.getId(), null)
            .subscribe().withSubscriber(AssertSubscriber.create(10));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceAnAddedBookmarkWithItsAuthor() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        Bookmark created = bookmarkService.createBookmark(new BookmarkSaveJson(
            collection.getId(), null, "Live", "https://example.com/live", null, Set.of()));

        // ASSERT
        assertThat(subscriber.getItems()).hasSize(1);
        CollectionEventJson event = subscriber.getItems().getFirst();
        assertThat(event.getKind()).isEqualTo(ChangeKind.BOOKMARK_ADDED);
        assertThat(event.getBookmarkId()).isEqualTo(created.getId());
        assertThat(event.getActorName())
            .as("BR-209: the indicator names the member who made the change")
            .isNotBlank();
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceARemovedBookmark() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = fixtureService.persistBookmark(b -> b.withCollection(collection));
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        bookmarkService.removeBookmark(bookmark.getId());

        // ASSERT — removeBookmark funnels through batchRemove, so the single
        // notification site still names the one bookmark that went
        assertThat(subscriber.getItems()).hasSize(1);
        assertThat(subscriber.getItems().getFirst().getKind()).isEqualTo(ChangeKind.BOOKMARK_REMOVED);
        assertThat(subscriber.getItems().getFirst().getBookmarkId()).isEqualTo(bookmark.getId());
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceABatchAsOneEventNamingNoSingleBookmark() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark first = persistBookmark(collection, "one");
        Bookmark second = persistBookmark(collection, "two");
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT — over HTTP rather than straight into the service: a batch loads its
        // bookmarks inside the same transaction that changes them, which calling
        // the service with entities from an earlier one would not reproduce
        String body = """
            {"collectionId":"%s","folderId":null,"bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(), first.getId().getUUID(), second.getId().getUUID());
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(200);

        // ASSERT — one frame per operation, not per bookmark: the client re-reads
        // the whole collection either way, and a large batch must not flood the
        // stream
        assertThat(subscriber.getItems()).hasSize(1);
        assertThat(subscriber.getItems().getFirst().getKind()).isEqualTo(ChangeKind.BOOKMARK_CHANGED);
        assertThat(subscriber.getItems().getFirst().getBookmarkId())
            .as("no single bookmark can be named when several changed")
            .isNull();
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldNotDeliverAChangeBackToTheTabThatMadeIt() {
        // ARRANGE — two tabs of the same user on the same collection
        Collection collection = fixtureService.createTestCollection();
        AssertSubscriber<CollectionEventJson> actingTab = broadcaster.subscribe(collection.getId(), "tab-1")
            .subscribe().withSubscriber(AssertSubscriber.create(10));
        AssertSubscriber<CollectionEventJson> otherTab = broadcaster.subscribe(collection.getId(), "tab-2")
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        // ACT — the write arrives over HTTP, which is the only way the tab id can
        // be carried (X-Client-Id); EventSource cannot send headers, hence the
        // query parameter on the stream itself
        String body = """
            {"collectionId":"%s","title":"From tab 1","url":"https://example.com/tab1"}
            """.formatted(collection.getId().getUUID());
        RestAssured.given()
            .contentType(ContentType.JSON)
            .header(OriginClientIdRequestFilter.HEADER, "tab-1")
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(200);

        // ASSERT — BR-205
        actingTab.assertHasNotReceivedAnyItem();
        assertThat(otherTab.getItems()).hasSize(1);
        assertThat(otherTab.getItems().getFirst().getOriginClientId()).isEqualTo("tab-1");
        actingTab.cancel();
        otherTab.cancel();
    }
}
