package org.linkweave.api.bookmark;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionAccessRepo;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class FolderResourceITest {


    @Inject
    FixtureService fixtureService;

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
}
