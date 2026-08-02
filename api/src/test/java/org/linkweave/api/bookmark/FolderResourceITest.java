package org.linkweave.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionAccessRepo;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.emailaddress.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class FolderResourceITest {


    @Inject
    FixtureService fixtureService;

    @Inject
    FolderRepo folderRepo;

    @Inject
    UserRepo userRepo;

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        // ARRANGE
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","name":"Folder"}
            """;
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
            // ASSERT
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateFolder_whenAuthenticatedAndHasAccess() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"My Folder"}
            """.formatted(collectionId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
            // ASSERT
            .then()
            .statusCode(200)
            .body("data.name", equalTo("My Folder"))
            .body("data.collectionId", notNullValue())
            .body("data.parentId", nullValue())
            .body("id", notNullValue())
            .body("entityInfo", notNullValue());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateSubfolder_whenParentIdProvided() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Folder parent = fixtureService.persistFolder(b -> b
            .withCollection(collection)
            .withName("Parent")
        );
        String parentId = parent.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","parentId":"%s","name":"Child Folder"}
            """.formatted(collectionId, parentId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
            // ASSERT
            .then()
            .statusCode(200)
            .body("data.name", equalTo("Child Folder"))
            .body("data.parentId", notNullValue())
            .body("data.collectionId", notNullValue());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldReturn403_whenUserHasNoCollectionAccess() {
        // ARRANGE
        String nonExistentId = java.util.UUID.randomUUID().toString();
        String body = """
            {"collectionId":"%s","name":"My Folder"}
            """.formatted(nonExistentId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldReturn403_whenRenamingFolderIntoForeignCollection() {
        // ARRANGE: the caller owns the folder ...
        Collection ownCollection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(ownCollection)
            .withName("My Folder"));

        // ... but names a collection they have no access to as the folder's new home.
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection foreignCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));

        String body = """
            {"collectionId":"%s","name":"Renamed"}
            """.formatted(foreignCollection.getId().getUUID());

        // ACT: IDOR attempt — rename must not re-home the folder (and its subtree)
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/folders/{folderId}", folder.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);

        Assertions.assertThat(folderRepo.getById(folder.getId()).getCollection().getId())
            .isEqualTo(ownCollection.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldReturn403_whenRenamingForeignFolderUsingOwnCollectionId() {
        // ARRANGE
        Collection ownCollection = fixtureService.createTestCollection();
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection foreignCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));
        Folder foreignFolder = fixtureService.persistFolder(b -> b
            .withCollection(foreignCollection)
            .withName("Alice's Folder"));

        String body = """
            {"collectionId":"%s","name":"Stolen"}
            """.formatted(ownCollection.getId().getUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/folders/{folderId}", foreignFolder.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);

        Assertions.assertThat(folderRepo.getById(foreignFolder.getId()).getName())
            .isEqualTo("Alice's Folder");
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldReturn403_whenReparentingUnderAForeignFolder() {
        // ARRANGE
        Collection ownCollection = fixtureService.createTestCollection();
        Folder folder = fixtureService.persistFolder(b -> b
            .withCollection(ownCollection)
            .withName("My Folder"));

        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection foreignCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));
        Folder foreignParent = fixtureService.persistFolder(b -> b
            .withCollection(foreignCollection)
            .withName("Alice's Folder"));

        String body = """
            {"collectionId":"%s","name":"My Folder","parentId":"%s"}
            """.formatted(ownCollection.getId().getUUID(), foreignParent.getId().getUUID());

        // ACT: a parent from another collection is an authorization failure, not a 500
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/folders/{folderId}", folder.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);

        Assertions.assertThat(folderRepo.getById(folder.getId()).getParent()).isNull();
    }
}
