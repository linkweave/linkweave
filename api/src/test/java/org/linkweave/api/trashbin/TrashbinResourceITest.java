package org.linkweave.api.trashbin;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class TrashbinResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        RestAssured.given().get("/trashbin").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldSoftDeleteBookmark_andShowItInTrashbin() {
        // ARRANGE
        RestAssured.given().delete("/trashbin");

        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String created = RestAssured.given().contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","title":"ToDelete-soft","url":"https://example.com"}
                """.formatted(collectionId))
            .post("/bookmarks").then().statusCode(200)
            .extract().path("id");

        // ACT
        RestAssured.given().delete("/bookmarks/" + created).then().statusCode(204);

        // ASSERT
        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/bookmarks").then().statusCode(200)
            .body("bookmarkList", hasSize(0));

        RestAssured.given().get("/trashbin").then().statusCode(200)
            .body("bookmarks", hasSize(1))
            .body("bookmarks[0].data.title", equalTo("ToDelete-soft"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldRestoreBookmark_fromTrashbin() {
        // ARRANGE
        Bookmark bookmark = fixtureService.createTestBookmark(b -> b.withTitle("Restorable"));
        RestAssured.given().delete("/bookmarks/" + bookmark.getId().getUUID()).then().statusCode(204);

        // ACT
        RestAssured.given()
            .post("/trashbin/bookmarks/" + bookmark.getId().getUUID() + "/restore")
            .then().statusCode(200)
            .body("data.title", equalTo("Restorable"))
            .body("deletedAt", equalTo(null));

        // ASSERT
        RestAssured.given().get("/trashbin").then().statusCode(200)
            .body("bookmarks.id", org.hamcrest.Matchers.not(hasItem(bookmark.getId().getUUID().toString())));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldPurgeBookmark_permanently() {
        // ARRANGE
        Bookmark bookmark = fixtureService.createTestBookmark(b -> b.withTitle("Purgeable"));
        RestAssured.given().delete("/bookmarks/" + bookmark.getId().getUUID()).then().statusCode(204);

        // ACT
        RestAssured.given()
            .delete("/trashbin/bookmarks/" + bookmark.getId().getUUID())
            .then().statusCode(204);

        // ASSERT
        RestAssured.given().get("/trashbin").then().statusCode(200)
            .body("bookmarks.id", org.hamcrest.Matchers.not(hasItem(bookmark.getId().getUUID().toString())));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldEmptyTrashbin() {
        // ARRANGE
        Bookmark a = fixtureService.createTestBookmark(b -> b.withTitle("A"));
        Bookmark c = fixtureService.createTestBookmark(b -> b.withTitle("C"));
        RestAssured.given().delete("/bookmarks/" + a.getId().getUUID()).then().statusCode(204);
        RestAssured.given().delete("/bookmarks/" + c.getId().getUUID()).then().statusCode(204);

        // ACT
        RestAssured.given().delete("/trashbin").then().statusCode(204);

        // ASSERT
        RestAssured.given().get("/trashbin").then().statusCode(200)
            .body("bookmarks", hasSize(0))
            .body("folders", hasSize(0));
        RestAssured.given().get("/trashbin/count").then().statusCode(200)
            .body("count", equalTo(0));
    }
}
