package org.chainlink.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class BookmarkBatchResourceITest {

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
    void shouldReturn401_whenBatchMoveNotAuthenticated() {
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","bookmarkIds":["bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]}
            """;
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldMoveAllBookmarksToFolder_whenBatchMove() {
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Target Folder")
        );
        Bookmark first = persistBookmark(collection, "first");
        Bookmark second = persistBookmark(collection, "second");

        String body = """
            {"collectionId":"%s","folderId":"%s","bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            folder.getId().getUUID(),
            first.getId().getUUID(),
            second.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(2))
            .body("bookmarkList.data.folderId",
                everyItem(equalTo(folder.getId().getUUID().toString())));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldMoveBookmarksToCollectionRoot_whenBatchMoveWithoutFolderId() {
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Source Folder")
        );
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withFolder(folder)
            .withTitle("filed")
            .withUrl("https://example.com/filed")
        );

        String body = """
            {"collectionId":"%s","folderId":null,"bookmarkIds":["%s"]}
            """.formatted(collection.getId().getUUID(), bookmark.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1))
            .body("bookmarkList[0].data.folderId", nullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403AndMoveNothing_whenBatchMoveContainsBookmarkOfOtherCollection() {
        Collection collection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Target Folder")
        );
        Bookmark own = persistBookmark(collection, "own");
        Bookmark foreign = persistBookmark(otherCollection, "foreign");

        String body = """
            {"collectionId":"%s","folderId":"%s","bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            folder.getId().getUUID(),
            own.getId().getUUID(),
            foreign.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(403);

        // Atomic rollback: the bookmark of the own collection must be untouched.
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList[0].data.folderId", nullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldSoftDeleteAllBookmarks_whenBatchDelete() {
        Collection collection = fixtureService.createTestCollection();
        Bookmark first = persistBookmark(collection, "first");
        Bookmark second = persistBookmark(collection, "second");

        String body = """
            {"collectionId":"%s","bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            first.getId().getUUID(),
            second.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            .then()
            .statusCode(204);

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403AndDeleteNothing_whenBatchDeleteContainsBookmarkOfOtherCollection() {
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

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            .then()
            .statusCode(403);

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldAddTagToAllBookmarks_whenBatchTag() {
        Collection collection = fixtureService.createTestCollection();
        Tag tag = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Batch Tag")
            .withColor("#00FF00")
        );
        Bookmark first = persistBookmark(collection, "first");
        Bookmark second = persistBookmark(collection, "second");

        String body = """
            {"collectionId":"%s","tagId":"%s","bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            tag.getId().getUUID(),
            first.getId().getUUID(),
            second.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(2))
            .body("bookmarkList[0].data.tagIds", contains(tag.getId().getUUID().toString()))
            .body("bookmarkList[1].data.tagIds", contains(tag.getId().getUUID().toString()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldKeepExistingTags_whenBatchTagAddsAnotherTag() {
        Collection collection = fixtureService.createTestCollection();
        Tag existing = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Existing")
            .withColor("#FF0000")
        );
        Tag added = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Added")
            .withColor("#00FF00")
        );
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("tagged")
            .withUrl("https://example.com/tagged")
            .withTags(new java.util.HashSet<>(java.util.Set.of(existing)))
        );

        String body = """
            {"collectionId":"%s","tagId":"%s","bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            added.getId().getUUID(),
            bookmark.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            .then()
            .statusCode(200)
            .body("bookmarkList[0].data.tagIds", containsInAnyOrder(
                existing.getId().getUUID().toString(),
                added.getId().getUUID().toString()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldFail_whenBatchTagWithTagOfOtherCollection() {
        Collection collection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Tag foreignTag = fixtureService.persistTag(b -> b
            .withCollection(otherCollection)
            .withName("Foreign Tag")
            .withColor("#0000FF")
        );
        Bookmark bookmark = persistBookmark(collection, "own");

        String body = """
            {"collectionId":"%s","tagId":"%s","bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            foreignTag.getId().getUUID(),
            bookmark.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            .then()
            .statusCode(403);

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList[0].data.tagIds", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenBatchDeleteWithEmptyBookmarkIds() {
        Collection collection = fixtureService.createTestCollection();

        String body = """
            {"collectionId":"%s","bookmarkIds":[]}
            """.formatted(collection.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenBatchMoveExceedsMaxSize() {
        Collection collection = fixtureService.createTestCollection();
        String ids = java.util.stream.IntStream.range(0, 501)
            .mapToObj(_ -> "\"" + java.util.UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));

        String body = """
            {"collectionId":"%s","folderId":null,"bookmarkIds":[%s]}
            """.formatted(collection.getId().getUUID(), ids);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403_whenMovingBookmarkOfOtherCollection() {
        Collection collection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Bookmark foreign = persistBookmark(otherCollection, "foreign");

        String body = """
            {"collectionId":"%s","folderId":null}
            """.formatted(collection.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .patch("/bookmarks/" + foreign.getId().getUUID() + "/move")
            .then()
            .statusCode(403);
    }

    private static final int BATCH_LOAD_SIZE = 500;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldNotDuplicateBookmarkInResponse_whenBookmarkHasMultipleTags() {
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Target")
        );
        Tag tag1 = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Tag One")
            .withColor("#FF0000")
        );
        Tag tag2 = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Tag Two")
            .withColor("#00FF00")
        );
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("multi-tagged")
            .withUrl("https://example.com/multi-tagged")
            .withTags(new java.util.HashSet<>(java.util.Set.of(tag1, tag2)))
        );

        String body = """
            {"collectionId":"%s","folderId":"%s","bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            folder.getId().getUUID(),
            bookmark.getId().getUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldBatchMove500BookmarksWithinTransactionLimit() {
        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Batch Target")
        );

        List<Bookmark> bookmarks = new ArrayList<>(BATCH_LOAD_SIZE);
        for (int i = 0; i < BATCH_LOAD_SIZE; i++) {
            bookmarks.add(persistBookmark(collection, "load-move-" + i));
        }

        String bookmarkIds = bookmarks.stream()
            .map(b -> "\"" + b.getId().getUUID() + "\"")
            .collect(Collectors.joining(","));

        String body = """
            {"collectionId":"%s","folderId":"%s","bookmarkIds":[%s]}
            """.formatted(
            collection.getId().getUUID(),
            folder.getId().getUUID(),
            bookmarkIds);

        long start = System.nanoTime();
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(BATCH_LOAD_SIZE))
            .body("bookmarkList.data.folderId",
                everyItem(equalTo(folder.getId().getUUID().toString())));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Batch move of %d bookmarks took %d ms%n", BATCH_LOAD_SIZE, elapsedMs);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldBatchDelete500BookmarksWithinTransactionLimit() {
        Collection collection = fixtureService.createTestCollection();

        List<Bookmark> bookmarks = new ArrayList<>(BATCH_LOAD_SIZE);
        for (int i = 0; i < BATCH_LOAD_SIZE; i++) {
            bookmarks.add(persistBookmark(collection, "load-delete-" + i));
        }

        String bookmarkIds = bookmarks.stream()
            .map(b -> "\"" + b.getId().getUUID() + "\"")
            .collect(Collectors.joining(","));

        String body = """
            {"collectionId":"%s","bookmarkIds":[%s]}
            """.formatted(
            collection.getId().getUUID(),
            bookmarkIds);

        long start = System.nanoTime();
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            .then()
            .statusCode(204);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Batch delete of %d bookmarks took %d ms%n", BATCH_LOAD_SIZE, elapsedMs);

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(0));
    }
}
