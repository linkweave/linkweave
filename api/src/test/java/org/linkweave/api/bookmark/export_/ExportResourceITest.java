package org.linkweave.api.bookmark.export_;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.linkweave.api.bookmark.Bookmark;
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

        fixtureService.persistFolder(b -> b.withCollection(collection).withName("Test Folder"));
        fixtureService.persistBookmark(b -> b.withCollection(collection).withTitle("Test Bookmark").withUrl("https://example.com"));

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
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Parent Folder"));
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

        // ACT
        String html = RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .extract().asString();

        // ASSERT
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
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        // ACT
        String html = RestAssured.given()
            .accept("text/html")
            .get("/collections/{collectionId}/export", collectionId)
            .then()
            .statusCode(200)
            .extract().asString();

        // ASSERT
        assertHtmlContainsNetscapeHeader(html);
        assert html.contains("<DL><p>");
        assert html.contains("</DL><p>");
    }

    @Test
    void shouldReturn401_whenNotAuthenticatedForPartialExport() {
        String collectionId = java.util.UUID.randomUUID().toString();

        RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody(java.util.UUID.randomUUID().toString()))
            .post("/collections/{collectionId}/export/partial", collectionId)
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReturn403_whenNoCollectionAccessForPartialExport() {
        String nonExistentId = java.util.UUID.randomUUID().toString();

        RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody(java.util.UUID.randomUUID().toString()))
            .post("/collections/{collectionId}/export/partial", nonExistentId)
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReturn403_whenBookmarkBelongsToAnotherCollectionForPartialExport() {
        // The user has access to BOTH collections (createTestCollection grants the
        // test user OWNER access), so this isolates the requireSameCollection guard:
        // access to the path collection must NOT let the caller export a bookmark
        // that lives in a different collection.
        Collection pathCollection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Bookmark foreign = fixtureService.persistBookmark(b -> b.withCollection(otherCollection)
            .withTitle("Foreign BM")
            .withUrl("https://foreign.com"));

        RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody(foreign.getId().getUUID().toString()))
            .post("/collections/{collectionId}/export/partial", pathCollection.getId().getUUID().toString())
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldSkipUnknownBookmarkIdsInPartialExport() {
        // ARRANGE
        // A read-only export degrades gracefully: a hard-purged / never-existing
        // id is skipped rather than aborting the whole export of the valid ones.
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Bookmark live = fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withTitle("Live BM")
            .withUrl("https://live.com"));
        String unknownId = java.util.UUID.randomUUID().toString();

        // ACT
        String html = RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody(live.getId().getUUID().toString(), unknownId))
            .accept("text/html")
            .post("/collections/{collectionId}/export/partial", collectionId)
            .then()
            .statusCode(200)
            .header("X-Exported-Count", "1")
            .extract().asString();

        // ASSERT
        assertContainsBookmark(html, "Live BM", "https://live.com");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldRejectEmptyBookmarkIdsForPartialExport() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody())
            .post("/collections/{collectionId}/export/partial", collectionId)
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldExportOnlySelectedBookmarks() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Bookmark kept = fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withTitle("Kept BM")
            .withUrl("https://kept.com"));
        fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withTitle("Skipped BM")
            .withUrl("https://skipped.com"));

        // ACT
        String html = RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody(kept.getId().getUUID().toString()))
            .accept("text/html")
            .post("/collections/{collectionId}/export/partial", collectionId)
            .then()
            .statusCode(200)
            .contentType("text/html")
            .header("Content-Disposition", containsString("attachment"))
            .extract().asString();

        // ASSERT
        assertHtmlContainsNetscapeHeader(html);
        assertContainsBookmark(html, "Kept BM", "https://kept.com");
        assert !html.contains("Skipped BM") : "unselected bookmark must not be exported";
        assert !html.contains("https://skipped.com");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldPreserveFolderAncestorsAndPruneEmptyBranchesInPartialExport() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Folder grandparent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Grandparent"));
        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection)
            .withName("Parent").withParent(grandparent));
        // A sibling branch under the grandparent that holds NO selected bookmark
        // — it must be pruned from the partial export.
        Folder emptySibling =
            fixtureService.persistFolder(b -> b.withCollection(collection).withName("Empty Sibling").withParent(grandparent));
        fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withFolder(emptySibling)
            .withTitle("Not Selected")
            .withUrl("https://not-selected.com"));
        Bookmark deep = fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withFolder(parent)
            .withTitle("Deep BM")
            .withUrl("https://deep.com"));

        // ACT
        String html = RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody(deep.getId().getUUID().toString()))
            .accept("text/html")
            .post("/collections/{collectionId}/export/partial", collectionId)
            .then()
            .statusCode(200)
            .extract().asString();

        // ASSERT
        // Ancestors of the selected bookmark's folder are kept.
        assertContainsFolder(html, "Grandparent");
        assertContainsFolder(html, "Parent");
        assertContainsBookmark(html, "Deep BM", "https://deep.com");
        // Empty/unselected branches are pruned.
        assert !html.contains("Empty Sibling") : "folders without selected bookmarks must be pruned";
        assert !html.contains("Not Selected");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldNotExportSoftDeletedBookmarks() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Bookmark live = fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withTitle("Live BM")
            .withUrl("https://live.com"));
        Bookmark trashed = fixtureService.persistBookmark(b -> b.withCollection(collection)
            .withTitle("Trashed BM")
            .withUrl("https://trashed.com"));

        // Soft-delete via the real batch-delete endpoint, simulating the bookmark
        // being trashed (e.g. in another tab) while still in the stale selection.
        RestAssured.given()
            .contentType("application/json")
            .body("{\"collectionId\":\"" + collectionId + "\",\"bookmarkIds\":[\"" + trashed.getId().getUUID() + "\"]}")
            .post("/bookmarks/batch-delete")
            .then()
            .statusCode(204);

        // ACT
        String html = RestAssured.given()
            .contentType("application/json")
            .body(partialExportBody(live.getId().getUUID().toString(), trashed.getId().getUUID().toString()))
            .accept("text/html")
            .post("/collections/{collectionId}/export/partial", collectionId)
            .then()
            .statusCode(200)
            .header("X-Exported-Count", "1")
            .extract().asString();

        // ASSERT
        assertContainsBookmark(html, "Live BM", "https://live.com");
        assert !html.contains("Trashed BM") : "soft-deleted bookmark must not be exported";
        assert !html.contains("https://trashed.com");
    }

    private Map<String, Object> partialExportBody(String... bookmarkIds) {
        return Map.of("bookmarkIds", List.of(bookmarkIds));
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
