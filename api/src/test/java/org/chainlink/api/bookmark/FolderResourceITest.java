package org.chainlink.api.bookmark;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderRepo;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccess;
import org.chainlink.api.collection.CollectionAccessRepo;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.api.collection.CollectionRole;
import org.chainlink.api.shared.user.User;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class FolderResourceITest {

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    UserRepo userRepo;

    @Inject
    FolderRepo folderRepo;

    private Collection createTestCollection() {
        User user = userRepo.findByEmail(EmailAddress.fromString("test@example.com")).orElseThrow();
        Collection collection = new Collection();
        collection.setName("Test Collection");
        collection.setOwner(user);
        collectionRepo.persist(collection);

        CollectionAccess access = new CollectionAccess();
        access.collection = collection;
        access.user = user;
        access.role = CollectionRole.OWNER;
        access.isDefault = true;
        collectionAccessRepo.persist(access);

        return collection;
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","name":"Folder"}
            """;
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateFolder_whenAuthenticatedAndHasAccess() {
        Collection collection = createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"My Folder"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
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
        Collection collection = createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Folder parent = new Folder();
        parent.collection = collection;
        parent.name = "Parent";
        folderRepo.persist(parent);
        String parentId = parent.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","parentId":"%s","name":"Child Folder"}
            """.formatted(collectionId, parentId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
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
        String nonExistentId = java.util.UUID.randomUUID().toString();
        String body = """
            {"collectionId":"%s","name":"My Folder"}
            """.formatted(nonExistentId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/folders")
            .then()
            .statusCode(403);
    }
}
