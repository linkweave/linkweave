package org.chainlink.api.bookmark.importbookmarks;

import java.io.File;
import java.nio.file.Path;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.chainlink.infrastructure.db.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@QuarkusTest
class ImportResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        String collectionId = java.util.UUID.randomUUID().toString();
        File file = getResourceFile("bookmarks-sample.html");

        RestAssured.given()
            .multiPart("file", file, "text/html")
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldImportBookmarksSuccessfully() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = getResourceFile("bookmarks-sample.html");

        RestAssured.given()
            .multiPart("file", file, "text/html")
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .body("foldersCreated", equalTo(2))
            .body("bookmarksCreated", equalTo(2))
            .body("importTag", notNullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldImportBookmarksWithRootLevelLinks() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = getResourceFile("bookmarks-with-root.html");

        RestAssured.given()
            .multiPart("file", file)
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .body("foldersCreated", equalTo(1))
            .body("bookmarksCreated", equalTo(3))
            .body("importTag", notNullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldImportEmptyBookmarksFile() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = getResourceFile("bookmarks-empty.html");

        RestAssured.given()
            .multiPart("file", file, "text/html")
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .body("foldersCreated", equalTo(0))
            .body("bookmarksCreated", equalTo(0))
            .body("importTag", notNullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenInvalidFileType() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = createTempFile("test.txt", "not a bookmark file");

        RestAssured.given()
            .multiPart("file", file)
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403_whenNoCollectionAccess() {
        String nonExistentId = java.util.UUID.randomUUID().toString();
        File file = getResourceFile("bookmarks-sample.html");

        RestAssured.given()
            .multiPart("file", file , "text/html")
            .post("/collections/{collectionId}/import", nonExistentId)
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldGenerateIncrementingImportTags() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = getResourceFile("bookmarks-sample.html");

        String firstTag = RestAssured.given()
            .multiPart("file", file, "text/html")
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .extract().path("importTag");

        String secondTag = RestAssured.given()
            .multiPart("file", file)
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .extract().path("importTag");

        Assertions.assertThat(firstTag).isNotNull();
        Assertions.assertThat(secondTag).isNotNull();
        Assertions.assertThat(firstTag).isNotEqualTo(secondTag);
        Assertions.assertThat(firstTag).matches("imported=\\d{4}-\\d{2}-\\d{2}_1");
        Assertions.assertThat(secondTag).matches("imported=\\d{4}-\\d{2}-\\d{2}_2");
    }

    private File getResourceFile(String name) {
        try {
            return Path.of(getClass().getClassLoader().getResource("__files/" + name).toURI()).toFile();
        } catch (Exception e) {
            throw new RuntimeException("Test fixture not found: " + name, e);
        }
    }

    private File createTempFile(String name, String content) {
        try {
            Path temp = java.nio.file.Files.createTempFile("test-", "-" + name);
            java.nio.file.Files.writeString(temp, content);
            temp.toFile().deleteOnExit();
            return temp.toFile();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp file", e);
        }
    }
}
