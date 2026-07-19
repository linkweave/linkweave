package org.linkweave.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.sortorder.SparseSortOrder;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.emailaddress.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

/**
 * UC-103: manual bookmark ordering within a folder group — append on create,
 * reorder via the move endpoint's explicit position, keep-number on a plain
 * move (BR-195).
 */
@QuarkusTest
class BookmarkReorderITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    BookmarkRepo bookmarkRepo;

    @Inject
    UserRepo userRepo;

    private static String createBookmark(String collectionId, String title) {
        return RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","title":"%s","url":"https://example.com/%s"}
                """.formatted(collectionId, title, title))
            .post("/bookmarks")
            .then()
            .statusCode(200)
            .extract().path("id");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldAppendNewBookmarksAtEnd() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        // ACT
        createBookmark(collectionId, "first");
        createBookmark(collectionId, "second");
        String third = createBookmark(collectionId, "third");

        // ASSERT: BR-196 — each new bookmark gets the last position in its group
        RestAssured.given()
            .get("/bookmarks/{id}", third)
            .then()
            .statusCode(200)
            .body("sortOrder", equalTo(3 * (int) SparseSortOrder.STEP));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReorderBookmark_whenDroppedBeforeFirstSibling() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String first = createBookmark(collectionId, "first");
        String mover = createBookmark(collectionId, "mover");

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorBookmarkId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, first))
            .patch("/bookmarks/{id}/move", mover)
            .then()
            .statusCode(200)
            // head slot before a group starting at STEP is 0 — a real position
            .body("sortOrder", equalTo(0));

        // ASSERT
        Assertions.assertThat(bookmarkRepo.findSiblings(collection.getId(), null))
            .extracting(Bookmark::getTitle)
            .containsExactly("mover", "first");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReorderBookmark_whenDroppedAfterSiblingUsingMidpoint() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String first = createBookmark(collectionId, "first");
        createBookmark(collectionId, "second");
        String mover = createBookmark(collectionId, "mover");

        // ACT: drop mover between first and second
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorBookmarkId":"%s","placement":"AFTER"}}
                """.formatted(collectionId, first))
            .patch("/bookmarks/{id}/move", mover)
            .then()
            .statusCode(200)
            // midpoint between first (1000) and second (2000)
            .body("sortOrder", equalTo(1500));

        // ASSERT
        Assertions.assertThat(bookmarkRepo.findSiblings(collection.getId(), null))
            .extracting(Bookmark::getTitle)
            .containsExactly("first", "mover", "second");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldKeepPositionNumber_whenMovedToFolderWithoutPosition() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Target"));
        fixtureService.persistBookmark(b -> b
            .withCollection(collection).withFolder(folder).withTitle("resident").withSortOrder(5000));
        String mover = createBookmark(collectionId, "mover");
        long sortOrderBefore = bookmarkRepo.getById(bookmarkId(mover)).getSortOrder();

        // ACT: plain move (drop onto the folder) — no explicit position
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","folderId":"%s"}
                """.formatted(collectionId, folder.getId().getUUID()))
            .patch("/bookmarks/{id}/move", mover)
            .then()
            .statusCode(200)
            // BR-195: the bookmark keeps its previous position number
            .body("sortOrder", equalTo((int) sortOrderBefore))
            .body("data.folderId", equalTo(folder.getId().getUUID().toString()));

        // ASSERT: ranked among the new group's bookmarks by the kept number
        Assertions.assertThat(bookmarkRepo.findSiblings(collection.getId(), folder.getId()))
            .extracting(Bookmark::getTitle)
            .containsExactly("mover", "resident");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldAssignExplicitPosition_whenDroppedBetweenBookmarksOfAnotherFolder() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Target"));
        Bookmark residentA = fixtureService.persistBookmark(b -> b
            .withCollection(collection).withFolder(folder).withTitle("resident A").withSortOrder(1000));
        fixtureService.persistBookmark(b -> b
            .withCollection(collection).withFolder(folder).withTitle("resident B").withSortOrder(2000));
        String mover = createBookmark(collectionId, "mover");

        // ACT: A2 — drop mover inside the folder, on the line after resident A
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","folderId":"%s","position":{"anchorBookmarkId":"%s","placement":"AFTER"}}
                """.formatted(collectionId, folder.getId().getUUID(), residentA.getId().getUUID()))
            .patch("/bookmarks/{id}/move", mover)
            .then()
            .statusCode(200)
            .body("data.folderId", equalTo(folder.getId().getUUID().toString()))
            .body("sortOrder", equalTo(1500));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldRenumberFolderGroup_whenGapIsExhausted() {
        // ARRANGE: two adjacent sort orders leave no midpoint between them
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        Bookmark first = fixtureService.persistBookmark(b -> b
            .withCollection(collection).withTitle("first").withSortOrder(10));
        fixtureService.persistBookmark(b -> b
            .withCollection(collection).withTitle("second").withSortOrder(11));
        Bookmark mover = fixtureService.persistBookmark(b -> b
            .withCollection(collection).withTitle("mover").withSortOrder(9999));

        // ACT: drop mover between first and second
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorBookmarkId":"%s","placement":"AFTER"}}
                """.formatted(collectionId, first.getId().getUUID()))
            .patch("/bookmarks/{id}/move", mover.getId().getUUID())
            .then()
            .statusCode(200)
            .body("sortOrder", equalTo(2 * (int) SparseSortOrder.STEP));

        // ASSERT: the whole group is renumbered in steps, order first, mover, second
        var siblings = bookmarkRepo.findSiblings(collection.getId(), null);
        Assertions.assertThat(siblings)
            .extracting(Bookmark::getTitle)
            .containsExactly("first", "mover", "second");
        Assertions.assertThat(siblings)
            .extracting(Bookmark::getSortOrder)
            .containsExactly(SparseSortOrder.STEP, 2 * SparseSortOrder.STEP, 3 * SparseSortOrder.STEP);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldOrderOlderBookmarkFirst_whenSortOrdersCollide() {
        // ARRANGE: a kept position number (BR-195) can collide with an existing one
        Collection collection = fixtureService.createTestCollection();
        Bookmark older = fixtureService.persistBookmark(b -> b
            .withCollection(collection).withTitle("older").withSortOrder(1000));
        Bookmark newer = fixtureService.persistBookmark(b -> b
            .withCollection(collection).withTitle("newer").withSortOrder(1000));
        fixtureService.setTimestampErstellt(older, java.time.OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        fixtureService.setTimestampErstellt(newer, java.time.OffsetDateTime.parse("2024-01-01T00:00:00Z"));

        // ASSERT: BR-198 — deterministic tie-break, older bookmark first
        Assertions.assertThat(bookmarkRepo.findSiblings(collection.getId(), null))
            .extracting(Bookmark::getTitle)
            .containsExactly("older", "newer");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldFail_whenAnchorIsNotInTargetFolderGroup() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Elsewhere"));
        Bookmark anchor = fixtureService.persistBookmark(b -> b
            .withCollection(collection).withFolder(folder).withTitle("anchor"));
        String mover = createBookmark(collectionId, "mover");

        // ACT: anchor lives in the folder, but the target group is unfiled
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorBookmarkId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, anchor.getId().getUUID()))
            .patch("/bookmarks/{id}/move", mover)
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldFail_whenAnchorIsTheMovedBookmarkItself() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String mover = createBookmark(collectionId, "mover");

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorBookmarkId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, mover))
            .patch("/bookmarks/{id}/move", mover)
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}
                """)
            .patch("/bookmarks/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/move")
            // ASSERT
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReturn403_whenReorderingForeignBookmarkUsingOwnCollectionId() {
        // ARRANGE: the attacker has a collection of their own ...
        Collection ownCollection = fixtureService.createTestCollection();
        String anchor = createBookmark(ownCollection.getId().getUUID().toString(), "anchor");

        // ... and targets a bookmark in alice's collection (no access).
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection foreignCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));
        Bookmark foreignBookmark = fixtureService.persistBookmark(b -> b
            .withCollection(foreignCollection)
            .withTitle("Alice's Bookmark"));

        // ACT: IDOR attempt — a bookmark the caller cannot see, moved with an accessible collectionId
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorBookmarkId":"%s","placement":"BEFORE"}}
                """.formatted(ownCollection.getId().getUUID(), anchor))
            .patch("/bookmarks/{id}/move", foreignBookmark.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);

        // the foreign bookmark must be untouched
        Assertions.assertThat(bookmarkRepo.getById(foreignBookmark.getId()).getSortOrder())
            .isEqualTo(foreignBookmark.getSortOrder());
    }

    private static org.linkweave.api.types.id.ID<Bookmark> bookmarkId(String uuid) {
        return org.linkweave.api.types.id.ID.of(java.util.UUID.fromString(uuid), Bookmark.class);
    }
}
