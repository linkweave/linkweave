package org.linkweave.api.trashbin;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class FolderTrashbinITest {

    @Inject
    FixtureService fixtureService;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldCascadeSoftDelete_folderWithBookmarksAndSubfolder() {
        // ARRANGE
        RestAssured.given().delete("/trashbin");

        Collection collection = fixtureService.createTestCollection();
        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("Parent"));
        Folder child = fixtureService.persistFolder(b -> b.withCollection(collection).withParent(parent).withName("Child"));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b.withCollection(collection).withFolder(parent).withTitle("InParent"));
        Bookmark childBookmark = fixtureService.persistBookmark(b -> b.withCollection(collection).withFolder(child).withTitle("InChild"));

        // ACT
        RestAssured.given().delete("/folders/" + parent.getId().getUUID()).then().statusCode(204);

        // ASSERT
        RestAssured.given().get("/trashbin").then().statusCode(200)
            .body("folders.id", hasItem(parent.getId().getUUID().toString()))
            .body("folders.id", hasItem(child.getId().getUUID().toString()))
            .body("bookmarks.id", hasItem(bookmark.getId().getUUID().toString()))
            .body("bookmarks.id", hasItem(childBookmark.getId().getUUID().toString()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldCascadeRestore_folderWithDescendants() {
        // ARRANGE
        RestAssured.given().delete("/trashbin");

        Collection collection = fixtureService.createTestCollection();
        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("RestoreParent"));
        Folder child = fixtureService.persistFolder(b -> b.withCollection(collection).withParent(parent).withName("RestoreChild"));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b.withCollection(collection).withFolder(child).withTitle("Cascaded"));

        RestAssured.given().delete("/folders/" + parent.getId().getUUID()).then().statusCode(204);
        // ACT
        RestAssured.given().post("/trashbin/folders/" + parent.getId().getUUID() + "/restore").then().statusCode(200);

        // ASSERT
        RestAssured.given().get("/trashbin").then().statusCode(200)
            .body("folders.id", not(hasItem(parent.getId().getUUID().toString())))
            .body("folders.id", not(hasItem(child.getId().getUUID().toString())))
            .body("bookmarks.id", not(hasItem(bookmark.getId().getUUID().toString())));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldRestoreToRoot_whenParentFolderStillDeleted() {
        // ARRANGE
        RestAssured.given().delete("/trashbin");

        Collection collection = fixtureService.createTestCollection();
        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("OuterDeleted"));
        Folder child = fixtureService.persistFolder(b -> b.withCollection(collection).withParent(parent).withName("InnerDeletedFirst"));

        // Delete child independently first
        RestAssured.given().delete("/folders/" + child.getId().getUUID()).then().statusCode(204);
        // Then delete parent (cascade marks it with a different timestamp on parent only)
        RestAssured.given().delete("/folders/" + parent.getId().getUUID()).then().statusCode(204);

        // ACT
        // Restore child while parent is still deleted -> child should detach to root
        RestAssured.given().post("/trashbin/folders/" + child.getId().getUUID() + "/restore").then().statusCode(200);

        // ASSERT
        // Verify child no longer references the deleted parent
        String childId = child.getId().getUUID().toString();
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/folders").then().statusCode(200)
            .body("folderList.find { it.id == '" + childId + "' }.data.parentId", nullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldCascadePurge_folderWithDescendants() {
        // ARRANGE
        RestAssured.given().delete("/trashbin");

        Collection collection = fixtureService.createTestCollection();
        Folder parent = fixtureService.persistFolder(b -> b.withCollection(collection).withName("PurgeParent"));
        Folder child = fixtureService.persistFolder(b -> b.withCollection(collection).withParent(parent).withName("PurgeChild"));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b.withCollection(collection).withFolder(child).withTitle("ToPurge"));

        RestAssured.given().delete("/folders/" + parent.getId().getUUID()).then().statusCode(204);
        // ACT
        RestAssured.given().delete("/trashbin/folders/" + parent.getId().getUUID()).then().statusCode(204);

        // ASSERT
        RestAssured.given().get("/trashbin").then().statusCode(200)
            .body("folders.id", not(hasItem(parent.getId().getUUID().toString())))
            .body("folders.id", not(hasItem(child.getId().getUUID().toString())))
            .body("bookmarks.id", not(hasItem(bookmark.getId().getUUID().toString())));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldReturnCombinedCount_forBookmarksAndFolders() {
        RestAssured.given().delete("/trashbin");

        Collection collection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b.withCollection(collection).withName("CountFolder"));
        Bookmark loose = fixtureService.persistBookmark(b -> b.withCollection(collection).withTitle("Loose"));

        RestAssured.given().delete("/folders/" + folder.getId().getUUID()).then().statusCode(204);
        RestAssured.given().delete("/bookmarks/" + loose.getId().getUUID()).then().statusCode(204);

        RestAssured.given().get("/trashbin/count").then().statusCode(200)
            .body("count", equalTo(2));
    }
}
