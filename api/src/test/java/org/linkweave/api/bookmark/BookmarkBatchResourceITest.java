package org.linkweave.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
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
        // ARRANGE
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","bookmarkIds":["bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]}
            """;
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldMoveAllBookmarksToFolder_whenBatchMove() {
        // ARRANGE
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

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(2))
            .body("bookmarkList.data.folderId",
                everyItem(equalTo(folder.getId().getUUID().toString())));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldMoveBookmarksToCollectionRoot_whenBatchMoveWithoutFolderId() {
        // ARRANGE
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

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1))
            .body("bookmarkList[0].data.folderId", nullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403AndMoveNothing_whenBatchMoveContainsBookmarkOfOtherCollection() {
        // ARRANGE
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

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
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
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark first = persistBookmark(collection, "first");
        Bookmark second = persistBookmark(collection, "second");

        String body = """
            {"collectionId":"%s","bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            first.getId().getUUID(),
            second.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            // ASSERT
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
            .post("/bookmarks/batch-delete")
            // ASSERT
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
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Tag tag = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Batch Tag")
            .withColor("#00FF00")
        );
        Bookmark first = persistBookmark(collection, "first");
        Bookmark second = persistBookmark(collection, "second");

        String body = """
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":[],"bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            tag.getId().getUUID(),
            first.getId().getUUID(),
            second.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(2))
            .body("bookmarkList[0].data.tagIds", contains(tag.getId().getUUID().toString()))
            .body("bookmarkList[1].data.tagIds", contains(tag.getId().getUUID().toString()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldKeepExistingTags_whenBatchTagAddsAnotherTag() {
        // ARRANGE
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
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":[],"bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            added.getId().getUUID(),
            bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList[0].data.tagIds", containsInAnyOrder(
                existing.getId().getUUID().toString(),
                added.getId().getUUID().toString()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldAddAndRemoveTagsInSingleBatch_whenBatchTag() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Tag keep = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Keep")
            .withColor("#FF0000")
        );
        Tag drop = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Drop")
            .withColor("#0000FF")
        );
        Tag add = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Add")
            .withColor("#00FF00")
        );
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("mixed")
            .withUrl("https://example.com/mixed")
            .withTags(new java.util.HashSet<>(java.util.Set.of(keep, drop)))
        );

        String body = """
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":["%s"],"bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            add.getId().getUUID(),
            drop.getId().getUUID(),
            bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList[0].data.tagIds", containsInAnyOrder(
                keep.getId().getUUID().toString(),
                add.getId().getUUID().toString()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenBatchTagAddsAndRemovesSameTag() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Tag tag = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Contradiction")
            .withColor("#00FF00")
        );
        Bookmark bookmark = persistBookmark(collection, "conflicted");

        String body = """
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":["%s"],"bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            tag.getId().getUUID(),
            tag.getId().getUUID(),
            bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturnSuccessAndMutateNothing_whenBatchTagHasNoTagIds() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = persistBookmark(collection, "no-op");

        String body = """
            {"collectionId":"%s","addTagIds":[],"removeTagIds":[],"bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1))
            // No tags added or removed — the early-exit leaves the bookmark alone.
            .body("bookmarkList[0].data.tagIds", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldFail_whenBatchTagWithTagOfOtherCollection() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Tag foreignTag = fixtureService.persistTag(b -> b
            .withCollection(otherCollection)
            .withName("Foreign Tag")
            .withColor("#0000FF")
        );
        Bookmark bookmark = persistBookmark(collection, "own");

        String body = """
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":[],"bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            foreignTag.getId().getUUID(),
            bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
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
    void shouldReturn500AndMutateNothing_whenBatchTagReferencesNonExistentTagId() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = persistBookmark(collection, "target");

        // A tag id that satisfies the UUID format but was never persisted.
        java.util.UUID ghostTagId = java.util.UUID.randomUUID();

        String body = """
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":[],"bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            ghostTagId,
            bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(500);

        // Atomic: the unknown id surfaced before any persist, so the bookmark
        // is untouched (no tags, no spurious mutation).
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
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();

        String body = """
            {"collectionId":"%s","bookmarkIds":[]}
            """.formatted(collection.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenBatchMoveExceedsMaxSize() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String ids = java.util.stream.IntStream.range(0, 501)
            .mapToObj(_ -> "\"" + java.util.UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));

        String body = """
            {"collectionId":"%s","folderId":null,"bookmarkIds":[%s]}
            """.formatted(collection.getId().getUUID(), ids);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403_whenMovingBookmarkOfOtherCollection() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Bookmark foreign = persistBookmark(otherCollection, "foreign");

        String body = """
            {"collectionId":"%s","folderId":null}
            """.formatted(collection.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .patch("/bookmarks/" + foreign.getId().getUUID() + "/move")
            // ASSERT
            .then()
            .statusCode(403);
    }

    private static final int BATCH_LOAD_SIZE = 500;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldNotDuplicateBookmarkInResponse_whenBookmarkHasMultipleTags() {
        // ARRANGE
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

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldBatchMove500BookmarksWithinTransactionLimit() {
        // ARRANGE
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

        // ACT
        long start = System.nanoTime();
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
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
        // ARRANGE
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

        // ACT
        long start = System.nanoTime();
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            // ASSERT
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

    // -------------------------------------------------------------------------
    // A5: a user with only BOOKMARK_READ must be rejected by the endpoint.
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenBatchMoveWithoutBookmarkWriteRole() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = persistBookmark(collection, "readonly");

        String body = """
            {"collectionId":"%s","folderId":null,"bookmarkIds":["%s"]}
            """.formatted(collection.getId().getUUID(), bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenBatchDeleteWithoutBookmarkWriteRole() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = persistBookmark(collection, "readonly");

        String body = """
            {"collectionId":"%s","bookmarkIds":["%s"]}
            """.formatted(collection.getId().getUUID(), bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenBatchTagWithoutBookmarkWriteRole() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Tag tag = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Read Only Tag")
            .withColor("#000000")
        );
        Bookmark bookmark = persistBookmark(collection, "readonly");

        String body = """
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":[],"bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            tag.getId().getUUID(),
            bookmark.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(403);
    }

    // -------------------------------------------------------------------------
    // #7: 401 when not authenticated (batch-delete / batch-tag).
    // -------------------------------------------------------------------------

    @Test
    void shouldReturn401_whenBatchDeleteNotAuthenticated() {
        // ARRANGE
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","bookmarkIds":["bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]}
            """;
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            // ASSERT
            .then()
            .statusCode(401);
    }

    @Test
    void shouldReturn401_whenBatchTagNotAuthenticated() {
        // ARRANGE
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","addTagIds":["cccccccc-cccc-cccc-cccc-cccccccccccc"],"removeTagIds":[],"bookmarkIds":["bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]}
            """;
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(401);
    }

    // -------------------------------------------------------------------------
    // #8: @Size(max = 500) boundary for batch-delete / batch-tag.
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenBatchDeleteExceedsMaxSize() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String ids = java.util.stream.IntStream.range(0, 501)
            .mapToObj(_ -> "\"" + java.util.UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));

        String body = """
            {"collectionId":"%s","bookmarkIds":[%s]}
            """.formatted(collection.getId().getUUID(), ids);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn400_whenBatchTagExceedsMaxSize() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Tag tag = fixtureService.persistTag(b -> b
            .withCollection(collection)
            .withName("Size Tag")
            .withColor("#000000")
        );
        String ids = java.util.stream.IntStream.range(0, 501)
            .mapToObj(_ -> "\"" + java.util.UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));

        String body = """
            {"collectionId":"%s","addTagIds":["%s"],"removeTagIds":[],"bookmarkIds":[%s]}
            """.formatted(collection.getId().getUUID(), tag.getId().getUUID(), ids);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-tag")
            // ASSERT
            .then()
            .statusCode(400);
    }

    // -------------------------------------------------------------------------
    // #9: cross-collection folder target rejection (BR-100).
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403AndMoveNothing_whenBatchMoveTargetsFolderOfOtherCollection() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Collection otherCollection = fixtureService.createTestCollection();
        Folder foreignFolder = fixtureService.persistFolder(b -> b
            .withCollection(otherCollection)
            .withName("Foreign Folder")
        );
        Bookmark own = persistBookmark(collection, "own");

        String body = """
            {"collectionId":"%s","folderId":"%s","bookmarkIds":["%s"]}
            """.formatted(
            collection.getId().getUUID(),
            foreignFolder.getId().getUUID(),
            own.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-move")
            // ASSERT
            .then()
            .statusCode(403);

        // Atomic rollback: the own bookmark must still have no folder.
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList[0].data.folderId", nullValue());
    }

    // -------------------------------------------------------------------------
    // #10: batch-deleted bookmarks appear in the trashbin and bump the count.
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldMoveBookmarksToTrashbin_whenBatchDelete() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark first = persistBookmark(collection, "trash-one");
        Bookmark second = persistBookmark(collection, "trash-two");

        String body = """
            {"collectionId":"%s","bookmarkIds":["%s","%s"]}
            """.formatted(
            collection.getId().getUUID(),
            first.getId().getUUID(),
            second.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks/batch-delete")
            // ASSERT
            .then()
            .statusCode(204);

        // The soft-deleted bookmarks appear in the trashbin with a deletedAt.
        RestAssured.given()
            .get("/trashbin")
            .then()
            .statusCode(200)
            .body("bookmarks.id", hasItems(
                first.getId().getUUID().toString(),
                second.getId().getUUID().toString()))
            .body("bookmarks.deletedAt", everyItem(notNullValue()));

        // The trashbin count reflects at least the two soft-deleted bookmarks.
        RestAssured.given()
            .get("/trashbin/count")
            .then()
            .statusCode(200)
            .body("count", greaterThanOrEqualTo(2));
    }
}
