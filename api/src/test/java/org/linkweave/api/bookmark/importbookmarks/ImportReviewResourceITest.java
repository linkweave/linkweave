package org.linkweave.api.bookmark.importbookmarks;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Slf4j
@QuarkusTest
class ImportReviewResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    @Inject
    BookmarkRepo bookmarkRepo;

    @Inject
    FolderRepo folderRepo;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturnManifestWithTotals() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        RestAssured.given()
            .multiPart("file", getResourceFile("bookmarks-sample.html"), "text/html")
            .post("/collections/{collectionId}/import/preview", collectionId)
            .then()
            .statusCode(200)
            .body("totalFolders", equalTo(2))
            .body("totalBookmarks", equalTo(2))
            .body("tree[0].type", equalTo("FOLDER"))
            .body("tree[0].name", equalTo("Bookmarks Bar"))
            .body("duplicateCount", equalTo(0))
            .body("unsupportedCount", equalTo(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldExcludeUnsupportedUrlsFromManifest() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        // chrome:// can't be stored → excluded from the tree, counted separately.
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>"
            + "<DT><A HREF=\"https://ok.example.com\">Keep</A>"
            + "<DT><A HREF=\"chrome://bookmarks/\">Browser page</A></DL><p>";

        RestAssured.given()
            .multiPart("file", "b.html", html.getBytes(), "text/html")
            .post("/collections/{collectionId}/import/preview", collectionId)
            .then()
            .statusCode(200)
            .body("totalBookmarks", equalTo(1))
            .body("unsupportedCount", equalTo(1))
            .body("tree", hasSize(1))
            .body("tree[0].name", equalTo("Keep"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenDestinationFolderDoesNotExist() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "skipDuplicates", false,
                "destinationFolderId", java.util.UUID.randomUUID().toString(),
                "nodes", List.of(Map.of(
                    "id", "b0", "type", "BOOKMARK", "name", "X", "url", "https://x.example.com"))))
            .post("/collections/{collectionId}/import/commit", collectionId)
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    @Transactional
    void shouldFlagDuplicatesAlreadyInCollection() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        // The sample's "https://example.com/" normalizes to "https://example.com"
        // (lone root slash stripped — parity with lib/url.ts).
        fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("Existing")
            .withUrl("https://example.com"));

        RestAssured.given()
            .multiPart("file", getResourceFile("bookmarks-sample.html"), "text/html")
            .post("/collections/{collectionId}/import/preview", collectionId)
            .then()
            .statusCode(200)
            .body("duplicateCount", equalTo(1))
            // "Bookmarks Bar" -> first child is the "Example Title" bookmark.
            .body("tree[0].children[0].name", equalTo("Example Title"))
            .body("tree[0].children[0].duplicate", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldFlagPercentEncodedUrlImportedTwice() {
        // Reproduces the reported bug: a URL with a percent-encoded query must
        // be flagged as a duplicate when its own file is re-imported. Server-side
        // detection keeps the raw (encoded) query, so it round-trips exactly.
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String encodedUrl =
            "https://auth.example.com/login?return_url=https%3A%2F%2Fauth.example.com%2Fapplications";

        Map<String, Object> node =
            Map.of("id", "b0", "type", "BOOKMARK", "name", "OAuth", "url", encodedUrl);
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", false, "nodes", List.of(node)))
            .post("/collections/{collectionId}/import/commit", collectionId)
            .then()
            .statusCode(200)
            .body("imported", equalTo(1));

        // Re-previewing a file with the same URL flags it as already in library.
        String html = "<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>"
            + "<DT><A HREF=\"" + encodedUrl + "\">OAuth</A></DL><p>";
        RestAssured.given()
            .multiPart("file", "bookmarks.html", html.getBytes(), "text/html")
            .post("/collections/{collectionId}/import/preview", collectionId)
            .then()
            .statusCode(200)
            .body("duplicateCount", equalTo(1))
            .body("tree[0].duplicate", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldClampOverlongAndBlankNamesInsteadOf500() {
        // ARRANGE
        // A crafted commit with a blank or >255-char name must not blow up on
        // persist (Bookmark.title is @NotBlank @Size(255)) — it's clamped/defaulted.
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String longName = "x".repeat(300);

        List<Map<String, Object>> nodes = List.of(
            Map.of("id", "b0", "type", "BOOKMARK", "name", "", "url", "https://blank-title.example.com"),
            Map.of("id", "b1", "type", "BOOKMARK", "name", longName, "url", "https://long-title.example.com"));

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", false, "nodes", nodes))
            .post("/collections/{collectionId}/import/commit", collectionId)
            .then()
            .statusCode(200)
            .body("imported", equalTo(2));

        // ASSERT
        Assertions.assertThat(bookmarkRepo.findByCollection(collection.getId()))
            .hasSize(2)
            .allSatisfy(b -> {
                Assertions.assertThat(b.getTitle()).isNotBlank();
                Assertions.assertThat(b.getTitle().length()).isLessThanOrEqualTo(255);
            });
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    @Transactional
    void shouldClampOverlongAndBlankFolderNamesInsteadOf500() {
        // ARRANGE
        // Folder.name is @NotBlank @Size(255) too — a crafted commit with a
        // blank or over-long folder name must be defaulted/clamped, not 500.
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String longName = "x".repeat(300);

        List<Map<String, Object>> nodes = List.of(
            Map.of("id", "f0", "type", "FOLDER", "name", "",
                "children", List.of(Map.of(
                    "id", "b0", "type", "BOOKMARK", "name", "InBlank",
                    "url", "https://blank-folder.example.com"))),
            Map.of("id", "f1", "type", "FOLDER", "name", longName,
                "children", List.of(Map.of(
                    "id", "b1", "type", "BOOKMARK", "name", "InLong",
                    "url", "https://long-folder.example.com"))));

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", false, "nodes", nodes))
            .post("/collections/{collectionId}/import/commit", collectionId)
            .then()
            .statusCode(200)
            .body("imported", equalTo(2))
            .body("foldersCreated", equalTo(2));

        // ASSERT
        Assertions.assertThat(folderRepo.findByCollection(collection.getId()))
            .hasSize(2)
            .allSatisfy(f -> {
                Assertions.assertThat(f.getName()).isNotBlank();
                Assertions.assertThat(f.getName().length()).isLessThanOrEqualTo(255);
            })
            // Blank → DEFAULT_FOLDER_NAME ("Imported"); over-long → clamped to 255 x's.
            .map(Folder::getName)
            .contains("Imported", "x".repeat(255));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldCommitOnlyKeptNodesAndMergeFolders() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        // One folder "Work" with two bookmarks; only one is kept.
        Map<String, Object> kept = Map.of(
            "id", "f0", "type", "FOLDER", "name", "Work",
            "children", List.of(
                Map.of("id", "b0", "type", "BOOKMARK", "name", "Kept", "url", "https://kept.example.com")
            ));

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", false, "nodes", List.of(kept)))
            .post("/collections/{collectionId}/import/commit", collectionId)
            .then()
            .statusCode(200)
            .body("imported", equalTo(1))
            .body("foldersCreated", equalTo(1))
            .body("duplicatesSkipped", equalTo(0));

        Assertions.assertThat(bookmarkRepo.findByCollection(collection.getId())).hasSize(1);
        Assertions.assertThat(folderRepo.findByCollection(collection.getId())).hasSize(1);

        // Re-importing the same folder name merges instead of creating "Work (2)".
        Map<String, Object> again = Map.of(
            "id", "f0", "type", "FOLDER", "name", "Work",
            "children", List.of(
                Map.of("id", "b0", "type", "BOOKMARK", "name", "Second", "url", "https://second.example.com")
            ));
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", false, "nodes", List.of(again)))
            .post("/collections/{collectionId}/import/commit", collectionId)
            .then()
            .statusCode(200)
            .body("imported", equalTo(1))
            .body("foldersCreated", equalTo(0));

        Assertions.assertThat(folderRepo.findByCollection(collection.getId())).hasSize(1);
        Assertions.assertThat(bookmarkRepo.findByCollection(collection.getId())).hasSize(2);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldSkipDuplicatesOnCommitWhenRequested() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("Existing")
            .withUrl("https://dup.example.com"));

        List<Map<String, Object>> nodes = List.of(
            Map.of("id", "b0", "type", "BOOKMARK", "name", "Dup", "url", "https://dup.example.com"),
            Map.of("id", "b1", "type", "BOOKMARK", "name", "New", "url", "https://new.example.com"));

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("skipDuplicates", true, "nodes", nodes))
            .post("/collections/{collectionId}/import/commit", collectionId)
            .then()
            .statusCode(200)
            .body("imported", equalTo(1))
            .body("duplicatesSkipped", equalTo(1));
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        String collectionId = java.util.UUID.randomUUID().toString();
        RestAssured.given()
            .multiPart("file", getResourceFile("bookmarks-sample.html"), "text/html")
            .post("/collections/{collectionId}/import/preview", collectionId)
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403_whenNoCollectionAccessOnPreview() {
        String nonExistentId = java.util.UUID.randomUUID().toString();
        RestAssured.given()
            .multiPart("file", getResourceFile("bookmarks-sample.html"), "text/html")
            .post("/collections/{collectionId}/import/preview", nonExistentId)
            .then()
            .statusCode(403);
    }

    private File getResourceFile(String name) {
        try {
            return Path.of(getClass().getClassLoader().getResource("__files/" + name).toURI()).toFile();
        } catch (Exception e) {
            throw new RuntimeException("Test fixture not found: " + name, e);
        }
    }
}
