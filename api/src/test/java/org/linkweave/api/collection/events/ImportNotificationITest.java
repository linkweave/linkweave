package org.linkweave.api.collection.events;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-104 for the bulk-add paths.
 *
 * <p>Both import services persist bookmarks directly rather than through
 * {@code BookmarkService}, so they do not inherit its notifications — which is
 * how importing stayed invisible to every other member of a shared collection
 * until they happened to reload. That is precisely the "stop reloading to find
 * out whether anything happened" case FR-100 exists for, so it is worth a test
 * per entry point rather than trusting the shared write path to cover it.
 */
@QuarkusTest
class ImportNotificationITest {

    @Inject
    CollectionEventBroadcaster broadcaster;

    @Inject
    FixtureService fixtureService;

    private AssertSubscriber<CollectionEventJson> listen(Collection collection) {
        return broadcaster.subscribe(collection.getId(), null)
            .subscribe().withSubscriber(AssertSubscriber.create(10));
    }

    private static File sampleFile() {
        return Path.of("src", "test", "resources", "__files", "bookmarks-sample.html").toFile();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceAFileImportOnceForTheWholeFile() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        RestAssured.given()
            .multiPart("file", sampleFile(), "text/html")
            .post("/collections/{collectionId}/import", collection.getId().getUUID())
            .then()
            .statusCode(200);

        // ASSERT — one frame for a file of many bookmarks, naming none of them
        assertThat(subscriber.getItems()).hasSize(1);
        assertThat(subscriber.getItems().getFirst().getKind()).isEqualTo(ChangeKind.BOOKMARK_ADDED);
        assertThat(subscriber.getItems().getFirst().getBookmarkId()).isNull();
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceAReviewedImportCommit() {
        // ARRANGE — the UC-096 path: the user picks what to import, then commits
        Collection collection = fixtureService.createTestCollection();
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT
        Map<String, Object> node = Map.of(
            "id", "b0", "type", "BOOKMARK", "name", "Reviewed", "url", "https://example.com/reviewed");
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", false, "nodes", List.of(node)))
            .post("/collections/{collectionId}/import/commit", collection.getId().getUUID())
            .then()
            .statusCode(200);

        // ASSERT
        assertThat(subscriber.getItems()).hasSize(1);
        assertThat(subscriber.getItems().getFirst().getKind()).isEqualTo(ChangeKind.BOOKMARK_ADDED);
        subscriber.cancel();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceNothingWhenACommitCreatesNothing() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        AssertSubscriber<CollectionEventJson> subscriber = listen(collection);

        // ACT — an empty selection is a no-op, not a change
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", false, "nodes", List.of()))
            .post("/collections/{collectionId}/import/commit", collection.getId().getUUID())
            .then()
            .statusCode(200);

        // ASSERT
        subscriber.assertHasNotReceivedAnyItem();
        subscriber.cancel();
    }
}
