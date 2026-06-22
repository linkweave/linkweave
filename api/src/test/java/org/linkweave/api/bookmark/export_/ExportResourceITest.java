package org.linkweave.api.bookmark.export_;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class ExportResourceITest {

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

        RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReturn403_whenNoCollectionAccess() {
        String nonExistentId = java.util.UUID.randomUUID().toString();

        RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", nonExistentId)
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReturn200WithNetscapeBookmarkFormat() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        fixtureService.persistFolder(b -> {
            b.withCollection(collection).withName("Test Folder");
        });
        fixtureService.persistBookmark(b -> {
            b.withCollection(collection).withTitle("Test Bookmark").withUrl("https://example.com");
        });

        RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .contentType("text/html")
            .header("Content-Disposition", containsString("attachment"))
            .body(containsString("NETSCAPE-Bookmark-file-1"))
            .body(containsString("<DT><H3"))
            .body(containsString("<DT><A HREF="))
            .body(containsString("</DL>"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldExportCollectionWithFoldersAndBookmarks() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Folder parent = fixtureService.persistFolder(b -> {
            b.withCollection(collection).withName("Parent Folder");
        });
        Folder child =
            fixtureService.persistFolder(b -> b.withCollection(collection).withName("Child Folder").withParent(parent));
        fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withFolder(parent)
            .withTitle("Parent BM 1")
            .withUrl("https://parent1.com")
            .withDescription("Parent bookmark description"));
        fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withFolder(child)
            .withTitle("Child BM 1")
            .withUrl("https://child1.com"));
        fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withTitle("Root BM")
            .withUrl("https://root.com"));

        String html = RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .extract().asString();

        assertHtmlContainsNetscapeHeader(html);
        assertContainsFolder(html, "Parent Folder");
        assertContainsFolder(html, "Child Folder");
        assertContainsBookmark(html, "Parent BM 1", "https://parent1.com");
        assertContainsDescription(html, "Parent bookmark description");
        assertContainsBookmark(html, "Child BM 1", "https://child1.com");
        assertContainsBookmark(html, "Root BM", "https://root.com");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldExportEmptyCollection() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String html = RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .extract().asString();

        assertHtmlContainsNetscapeHeader(html);
        assert html.contains("<DL><p>");
        assert html.contains("</DL><p>");
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
}
