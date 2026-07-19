package org.linkweave.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRole;
import org.linkweave.api.shared.sortorder.SparseSortOrder;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.emailaddress.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * UC-102: manual folder ordering — append on create, reorder via the move
 * endpoint's explicit position, keep-number on plain reparent.
 */
@QuarkusTest
class FolderReorderITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    FolderRepo folderRepo;

    @Inject
    UserRepo userRepo;

    private static String createFolder(String collectionId, String name) {
        return RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","name":"%s"}
                """.formatted(collectionId, name))
            .post("/folders")
            .then()
            .statusCode(200)
            .extract().path("id");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldAppendNewFoldersAtEnd_andListThemInCreationOrder() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        createFolder(collectionId, "Alpha");
        createFolder(collectionId, "Beta");
        createFolder(collectionId, "Gamma");

        // ACT
        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/folders")
            // ASSERT
            .then()
            .statusCode(200)
            .body("folderList.data.name", contains("Alpha", "Beta", "Gamma"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReorderFolder_whenDroppedBeforeFirstSibling() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String alpha = createFolder(collectionId, "Alpha");
        createFolder(collectionId, "Beta");
        String gamma = createFolder(collectionId, "Gamma");

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, alpha))
            .patch("/folders/{id}/move", gamma)
            .then()
            .statusCode(200);

        // ASSERT
        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/folders")
            .then()
            .statusCode(200)
            .body("folderList.data.name", contains("Gamma", "Alpha", "Beta"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReorderFolder_whenDroppedAfterSiblingUsingMidpoint() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String alpha = createFolder(collectionId, "Alpha");
        createFolder(collectionId, "Beta");
        String gamma = createFolder(collectionId, "Gamma");

        // ACT: drop Gamma between Alpha and Beta (after Alpha)
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"AFTER"}}
                """.formatted(collectionId, alpha))
            .patch("/folders/{id}/move", gamma)
            .then()
            .statusCode(200)
            // midpoint between Alpha (1000) and Beta (2000)
            .body("sortOrder", equalTo(1500));

        // ASSERT
        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/folders")
            .then()
            .statusCode(200)
            .body("folderList.data.name", contains("Alpha", "Gamma", "Beta"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldKeepPositionNumber_whenReparentedWithoutPosition() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String parent = createFolder(collectionId, "Parent");
        createFolder(collectionId, "Beta");
        String gamma = createFolder(collectionId, "Gamma");
        long sortOrderBefore = folderRepo.getById(folderId(gamma)).getSortOrder();

        // ACT: plain nest (drop *onto* Parent) — no explicit position
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","parentId":"%s"}
                """.formatted(collectionId, parent))
            .patch("/folders/{id}/move", gamma)
            .then()
            .statusCode(200)
            // BR-189: the folder keeps its previous position number
            .body("sortOrder", equalTo((int) sortOrderBefore))
            .body("data.parentId", equalTo(parent));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldAssignExplicitPosition_whenDroppedBetweenChildrenOfNewParent() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Parent"));
        String parentId = parent.getId().getUUID().toString();
        Folder childA = fixtureService.persistFolder(b -> b
            .withCollection(collection).withParent(parent).withName("Child A").withSortOrder(1000));
        fixtureService.persistFolder(b -> b
            .withCollection(collection).withParent(parent).withName("Child B").withSortOrder(2000));
        String mover = createFolder(collectionId, "Mover");

        // ACT: drop Mover inside Parent, on the line after Child A
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","parentId":"%s","position":{"anchorFolderId":"%s","placement":"AFTER"}}
                """.formatted(collectionId, parentId, childA.getId().getUUID()))
            .patch("/folders/{id}/move", mover)
            .then()
            .statusCode(200)
            .body("data.parentId", equalTo(parentId))
            .body("sortOrder", equalTo(1500));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldRenumberSiblingGroup_whenGapIsExhausted() {
        // ARRANGE: two adjacent sort orders leave no midpoint between them
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        Folder first = fixtureService.persistFolder(b -> b
            .withCollection(collection).withName("First").withSortOrder(10));
        fixtureService.persistFolder(b -> b
            .withCollection(collection).withName("Second").withSortOrder(11));
        Folder mover = fixtureService.persistFolder(b -> b
            .withCollection(collection).withName("Mover").withSortOrder(9999));

        // ACT: drop Mover between First and Second
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"AFTER"}}
                """.formatted(collectionId, first.getId().getUUID()))
            .patch("/folders/{id}/move", mover.getId().getUUID())
            .then()
            .statusCode(200)
            .body("sortOrder", equalTo(2 * (int) SparseSortOrder.STEP));

        // ASSERT: the whole sibling group is renumbered in steps, order First, Mover, Second
        var siblings = folderRepo.findSiblings(collection.getId(), null);
        Assertions.assertThat(siblings)
            .extracting(Folder::getName)
            .containsExactly("First", "Mover", "Second");
        Assertions.assertThat(siblings)
            .extracting(Folder::getSortOrder)
            .containsExactly(SparseSortOrder.STEP, 2 * SparseSortOrder.STEP, 3 * SparseSortOrder.STEP);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldFail_whenAnchorIsNotASiblingUnderTargetParent() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Parent"));
        Folder child = fixtureService.persistFolder(b -> b
            .withCollection(collection).withParent(parent).withName("Child"));
        String mover = createFolder(collectionId, "Mover");

        // ACT: anchor lives under Parent, but the target parent is the root level
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, child.getId().getUUID()))
            .patch("/folders/{id}/move", mover)
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldFail_whenAnchorBelongsToForeignCollection() {
        // ARRANGE: the anchor lives in alice's collection — indistinguishable
        // from any other non-sibling anchor (uniform 400, no existence probe)
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String mover = createFolder(collectionId, "Mover");
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection foreignCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));
        Folder foreignAnchor = fixtureService.persistFolder(b -> b
            .withCollection(foreignCollection)
            .withName("Alice's Folder"));

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, foreignAnchor.getId().getUUID()))
            .patch("/folders/{id}/move", mover)
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldFail_whenAnchorIsTheMovedFolderItself() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String mover = createFolder(collectionId, "Mover");

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, mover))
            .patch("/folders/{id}/move", mover)
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "alice@example.com", roles = { "BOOKMARK_READ" })
    void shouldServeSameManualOrderToOtherCollectionMembers() {
        // ARRANGE: a collection owned by the seeded test user whose folders were
        // reordered (persisted sortOrder differs from creation order); seeded
        // alice only has member access (BR-188: the order is shared collection
        // data — every member sees the same order)
        Collection collection = fixtureService.createTestCollection();
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(collection)
            .withUser(alice)
            .withRole(CollectionRole.MEMBER)
            .withDefault(false));
        fixtureService.persistFolder(b -> b.withCollection(collection).withName("Gamma").withSortOrder(1000));
        fixtureService.persistFolder(b -> b.withCollection(collection).withName("Alpha").withSortOrder(2000));
        fixtureService.persistFolder(b -> b.withCollection(collection).withName("Beta").withSortOrder(3000));

        // ACT
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/folders")
            // ASSERT
            .then()
            .statusCode(200)
            .body("folderList.data.name", contains("Gamma", "Alpha", "Beta"));
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}
                """)
            .patch("/folders/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/move")
            // ASSERT
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReturn403_whenUserHasNoCollectionAccess() {
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s"}
                """.formatted(java.util.UUID.randomUUID()))
            .patch("/folders/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/move")
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldPersistZeroSortOrder_whenDroppedBeforeTheFirstFolder() {
        // ARRANGE: a backfilled group starts at STEP, so the head slot is STEP - STEP = 0
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        String alpha = createFolder(collectionId, "Alpha");
        String beta = createFolder(collectionId, "Beta");

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"BEFORE"}}
                """.formatted(collectionId, alpha))
            .patch("/folders/{id}/move", beta)
            .then()
            .statusCode(200)
            .body("sortOrder", equalTo(0));

        // ASSERT: zero is a real position, not an "unset" marker — it sorts first
        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/folders")
            .then()
            .statusCode(200)
            .body("folderList.data.name", contains("Beta", "Alpha"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldKeepExplicitZeroSortOrder_whenSetViaTheFixtureBuilder() {
        // ARRANGE: 0 must survive the fixture's "append when unset" convenience,
        // otherwise a test modelling a head-inserted folder would silently be
        // handed an appended one instead.
        Collection collection = fixtureService.createTestCollection();
        Folder head = fixtureService.persistFolder(b -> b
            .withCollection(collection).withName("Head").withSortOrder(0));
        Folder appended = fixtureService.persistFolder(b -> b
            .withCollection(collection).withName("Appended"));

        // ASSERT
        Assertions.assertThat(folderRepo.getById(head.getId()).getSortOrder()).isZero();
        Assertions.assertThat(folderRepo.getById(appended.getId()).getSortOrder())
            .isEqualTo(SparseSortOrder.STEP);
        Assertions.assertThat(folderRepo.findSiblings(collection.getId(), null))
            .extracting(Folder::getName)
            .containsExactly("Head", "Appended");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = { "BOOKMARK_WRITE" })
    void shouldReturn403_whenMovingForeignFolderUsingOwnCollectionId() {
        // ARRANGE: the attacker has a collection of their own ...
        Collection ownCollection = fixtureService.createTestCollection();
        String anchor = createFolder(ownCollection.getId().getUUID().toString(), "Anchor");

        // ... and targets a folder in alice's collection (no access).
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection foreignCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));
        Folder foreignFolder = fixtureService.persistFolder(b -> b
            .withCollection(foreignCollection)
            .withName("Alice's Folder"));

        // ACT: IDOR attempt — a folder the caller cannot see, moved with an accessible collectionId
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("""
                {"collectionId":"%s","position":{"anchorFolderId":"%s","placement":"BEFORE"}}
                """.formatted(ownCollection.getId().getUUID(), anchor))
            .patch("/folders/{id}/move", foreignFolder.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);

        // the foreign folder must be untouched
        Assertions.assertThat(folderRepo.getById(foreignFolder.getId()).getSortOrder())
            .isEqualTo(foreignFolder.getSortOrder());
    }

    private static org.linkweave.api.types.id.ID<Folder> folderId(String uuid) {
        return org.linkweave.api.types.id.ID.of(java.util.UUID.fromString(uuid), Folder.class);
    }
}
