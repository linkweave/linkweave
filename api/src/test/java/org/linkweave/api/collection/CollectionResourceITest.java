package org.linkweave.api.collection;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class CollectionResourceITest {

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .get("/collections/{id}")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturn403_whenNoCollectionAccess() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .get("/collections/{id}")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturnCollectionInfo_whenUserHasAccess() {
        Collection collection = fixtureService.createTestCollection();

        RestAssured.given()
            .pathParam("id", collection.getId().getUUID().toString())
            .get("/collections/{id}")
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("name", equalTo("Test Collection"))
            .body("bookmarks", hasSize(0))
            .body("folders", hasSize(0))
            .body("tags", hasSize(0));
    }
}
