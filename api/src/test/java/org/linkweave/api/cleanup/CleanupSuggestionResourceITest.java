package org.linkweave.api.cleanup;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CleanupSuggestionResourceITest {

    @Inject
    FixtureService fixtureService;

    private Bookmark persistBookmark(Collection collection, String title) {
        return fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle(title)
            .withUrl("https://example.com/" + title)
        );
    }

    @Test
    void shouldReturn401_whenMoveToTrashNotAuthenticated() {
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","bookmarkIds":["bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]}
            """;
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/cleanup-suggestions/move-to-trash")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldSoftDeleteBookmarks_whenMoveToTrash() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = persistBookmark(collection, "stale");

        String body = """
            {"collectionId":"%s","bookmarkIds":["%s"]}
            """.formatted(collection.getId().getUUID(), bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/cleanup-suggestions/move-to-trash")
            .then()
            .statusCode(204);

        // ASSERT
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403AndDeleteNothing_whenMoveToTrashContainsBookmarkOfOtherCollection() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Bookmark own = persistBookmark(collection, "own");
        Bookmark foreign = persistBookmark(otherCollection, "foreign");

        String body = """
            {"collectionId":"%s","bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            own.getId().getUUID(),
            foreign.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/cleanup-suggestions/move-to-trash")
            .then()
            .statusCode(403);

        // ASSERT
        // Atomic rollback: neither bookmark was deleted.
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1));
        RestAssured.given()
            .queryParam("collectionId", otherCollection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturnAvailableThresholds() {
        RestAssured.given()
            .get("/cleanup-suggestions/thresholds")
            .then()
            .statusCode(200)
            .body("thresholds", notNullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturnEmptySuggestions_whenNoStaleBookmarks() {
        Collection collection = fixtureService.createTestCollection();
        persistBookmark(collection, "fresh");

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/cleanup-suggestions")
            .then()
            .statusCode(200)
            .body("suggestions", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldUseDefaultThreshold_whenInvalidThresholdProvided() {
        Collection collection = fixtureService.createTestCollection();

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .queryParam("thresholdMonths", 999)
            .get("/cleanup-suggestions")
            .then()
            .statusCode(200)
            .body("suggestions", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDismissSuggestion() {
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = persistBookmark(collection, "to-dismiss");

        RestAssured.given()
            .post("/cleanup-suggestions/{bookmarkId}/dismiss", bookmark.getId().getUUID().toString())
            .then()
            .statusCode(204);
    }

    @Test
    void shouldReturn401_whenListNotAuthenticated() {
        RestAssured.given()
            .queryParam("collectionId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
            .get("/cleanup-suggestions")
            .then()
            .statusCode(401);
    }
}
