package org.linkweave.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionAccessRepo;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class BookmarkResourceITest {

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    FolderRepo folderRepo;

    @Inject
    TagRepo tagRepo;

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenGetBookmarksNotAuthenticated() {
        RestAssured.given()
            .queryParam("collectionId", java.util.UUID.randomUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturnEmptyList_whenCollectionHasNoBookmarks() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        // ACT
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(0));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturnBookmarks_whenCollectionHasBookmarks() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","title":"Test","url":"https://example.com"}
            """.formatted(collectionId);
        RestAssured.given().contentType(ContentType.JSON).body(body).post("/bookmarks");

        // ACT
        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1))
            .body("bookmarkList[0].data.title", equalTo("Test"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturnBookmarksOrderedByCreationDateDesc() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"First\",\"url\":\"https://first.com\"}".formatted(collectionId))
            .post("/bookmarks");
        RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"Second\",\"url\":\"https://second.com\"}".formatted(collectionId))
            .post("/bookmarks");

        // ACT
        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(2))
            .body("bookmarkList[0].data.title", equalTo("Second"))
            .body("bookmarkList[1].data.title", equalTo("First"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturn403_whenGetBookmarksWithoutCollectionAccess() {
        RestAssured.given()
            .queryParam("collectionId", java.util.UUID.randomUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(403);
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        // ARRANGE
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","title":"Test","url":"https://example.com"}
            """;
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            // ASSERT
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithoutFolder_whenFolderIdIsNull() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","title":"Unfiled Bookmark","url":"https://example.org","folderId":null}
            """.formatted(collectionId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200)
            .body("data.folderId", nullValue())
            .body("data.title", equalTo("Unfiled Bookmark"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithFolder_whenFolderIdProvided() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Test Folder")
        );
        String folderId = folder.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","folderId":"%s","title":"Filed Bookmark","url":"https://example.org"}
            """.formatted(collectionId, folderId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200)
            .body("data.folderId", notNullValue())
            .body("data.title", equalTo("Filed Bookmark"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithTags_whenTagIdsProvided() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Tag tag = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Test Tag")
            .withColor("#FF0000")
        );
        String tagId = tag.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","title":"Tagged Bookmark","url":"https://example.org","tagIds":["%s"]}
            """.formatted(collectionId, tagId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200)
            .body("data.tagIds", hasSize(1))
            .body("data.title", equalTo("Tagged Bookmark"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithDescription_whenDescriptionProvided() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","title":"Bookmark with Description","url":"https://example.org","description":"This is my description"}
            """.formatted(collectionId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            // ASSERT
            .then()
            .statusCode(200)
            .body("data.description", equalTo("This is my description"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldReturn403_whenUserHasNoCollectionAccess() {
        // ARRANGE
        String nonExistentId = java.util.UUID.randomUUID().toString();
        String body = """
            {"collectionId":"%s","title":"My Bookmark","url":"https://example.com"}
            """.formatted(nonExistentId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            // ASSERT
            .then()
            .statusCode(403);
    }
}
