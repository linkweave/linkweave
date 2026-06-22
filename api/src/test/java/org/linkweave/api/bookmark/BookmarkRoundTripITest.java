package org.linkweave.api.bookmark;

import java.io.File;
import java.nio.file.Path;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class BookmarkRoundTripITest {

    @Inject
    FixtureService fixtureService;
    @Inject
    DatabaseService databaseService;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRoundTrip_bookmarksSampleFile() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = getResourceFile("bookmarks-sample.html");

        RestAssured.given()
            .multiPart("file", file, "text/html")
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .body("foldersCreated", equalTo(2))
            .body("bookmarksCreated", equalTo(2));

        String exportedHtml = RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .contentType("text/html")
            .extract().asString();

        assertHtmlContainsNetscapeHeader(exportedHtml);
        assertContainsFolder(exportedHtml, "Bookmarks Bar");
        assertContainsFolder(exportedHtml, "Subfolder");
        assertContainsBookmark(exportedHtml, "Example Title", "https://example.com/");
        assertContainsDescription(exportedHtml, "A sample description for the example bookmark");
        assertContainsBookmark(exportedHtml, "Another Title", "https://example.org");
        assertContainsDescription(exportedHtml, "Description for another title");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRoundTrip_bookmarksWithRootFile() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = getResourceFile("bookmarks-with-root.html");

        RestAssured.given()
            .multiPart("file", file, "text/html")
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .body("foldersCreated", equalTo(1))
            .body("bookmarksCreated", equalTo(3));

        String exportedHtml = RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .contentType("text/html")
            .extract().asString();

        assertHtmlContainsNetscapeHeader(exportedHtml);
        assertContainsFolder(exportedHtml, "Folder A");
        assertContainsBookmark(exportedHtml, "Root Bookmark 1", "https://root-bookmark.com/");
        assertContainsDescription(exportedHtml, "Root bookmark 1 description");
        assertContainsBookmark(exportedHtml, "Root Bookmark 2", "https://root-bookmark.org/");
        assertContainsBookmark(exportedHtml, "Folder A Bookmark", "https://folder-a.com/");
        assertContainsDescription(exportedHtml, "Folder A bookmark description");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRoundTrip_emptyBookmarksFile() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        File file = getResourceFile("bookmarks-empty.html");

        RestAssured.given()
            .multiPart("file", file, "text/html")
            .post("/collections/{collectionId}/import", collectionId)
            .then()
            .statusCode(200)
            .body("foldersCreated", equalTo(0))
            .body("bookmarksCreated", equalTo(0));

        String exportedHtml = RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .contentType("text/html")
            .extract().asString();

        assertHtmlContainsNetscapeHeader(exportedHtml);
        assert exportedHtml.contains("<DL><p>");
        assert exportedHtml.contains("</DL><p>");
    }

    private void assertHtmlContainsNetscapeHeader(String html) {
        assert html.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>");
        assert html.contains("<TITLE>Bookmarks</TITLE>");
        assert html.contains("<H1>Bookmarks</H1>");
    }

    private void assertContainsFolder(String html, String folderName) {
        assert html.contains("<DT><H3") && html.contains(folderName + "</H3>");
    }

    private void assertContainsBookmark(String html, String title, String url) {
        assert html.contains("<DT><A HREF=\"" + url + "\"");
        assert html.contains(">" + title + "</A>");
    }

    private void assertContainsDescription(String html, String description) {
        assert html.contains("<DD>" + description);
    }

    private File getResourceFile(String name) {
        try {
            return Path.of(getClass().getClassLoader().getResource("__files/" + name).toURI()).toFile();
        } catch (Exception e) {
            throw new RuntimeException("Test fixture not found: " + name, e);
        }
    }
}
