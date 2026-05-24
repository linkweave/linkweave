package org.chainlink.api.bookmark;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class SavedSearchResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenListNotAuthenticated() {
        RestAssured.given()
            .queryParam("collectionId", UUID.randomUUID().toString())
            .get("/saved-searches")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenListWithoutCollectionAccess() {
        RestAssured.given()
            .queryParam("collectionId", UUID.randomUUID().toString())
            .get("/saved-searches")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnEmptyList_whenCollectionHasNone() {
        Collection collection = fixtureService.createTestCollection();
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/saved-searches")
            .then()
            .statusCode(200)
            .body("savedSearchList", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldCreateSavedSearch() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Recent","query":"#work created:>2024-01-01"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/saved-searches")
            .then()
            .statusCode(200)
            .body("data.name", equalTo("Recent"))
            .body("data.query", equalTo("#work created:>2024-01-01"))
            .body("data.collectionId", equalTo(collectionId));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectDuplicateName_withinSameCollection() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Duplicate","query":"#a"}
            """.formatted(collectionId);

        RestAssured.given().contentType(ContentType.JSON).body(body).post("/saved-searches")
            .then().statusCode(200);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/saved-searches")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldAllowSameName_inDifferentCollections() {
        Collection a = fixtureService.createTestCollection();
        Collection b = fixtureService.createTestCollection();

        String bodyA = """
            {"collectionId":"%s","name":"Shared","query":"#x"}
            """.formatted(a.getId().getUUID().toString());
        String bodyB = """
            {"collectionId":"%s","name":"Shared","query":"#x"}
            """.formatted(b.getId().getUUID().toString());

        RestAssured.given().contentType(ContentType.JSON).body(bodyA).post("/saved-searches")
            .then().statusCode(200);
        RestAssured.given().contentType(ContentType.JSON).body(bodyB).post("/saved-searches")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldUpdateNameAndQuery() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String createBody = """
            {"collectionId":"%s","name":"Old","query":"#old"}
            """.formatted(collectionId);

        String id = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .post("/saved-searches")
            .then()
            .statusCode(200)
            .extract().path("id");

        String updateBody = """
            {"collectionId":"%s","name":"New","query":"#new"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .put("/saved-searches/" + id)
            .then()
            .statusCode(200)
            .body("data.name", equalTo("New"))
            .body("data.query", equalTo("#new"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectBlankQuery() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Blank","query":"   "}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/saved-searches")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectMovingBetweenCollections() {
        Collection source = fixtureService.createTestCollection();
        Collection target = fixtureService.createTestCollection();
        String sourceId = source.getId().getUUID().toString();
        String targetId = target.getId().getUUID().toString();

        String id = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"Movable\",\"query\":\"#x\"}".formatted(sourceId))
            .post("/saved-searches").then().statusCode(200).extract().path("id");

        // Attempt to move it to the target collection — must be rejected.
        String moveBody = """
            {"collectionId":"%s","name":"Movable","query":"#x"}
            """.formatted(targetId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(moveBody)
            .put("/saved-searches/" + id)
            .then()
            .statusCode(400);

        // The saved search must still belong to the source collection.
        RestAssured.given()
            .queryParam("collectionId", sourceId)
            .get("/saved-searches")
            .then()
            .statusCode(200)
            .body("savedSearchList", hasSize(1))
            .body("savedSearchList[0].data.collectionId", equalTo(sourceId));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDeleteSavedSearch() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String id = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"ToDelete\",\"query\":\"#x\"}".formatted(collectionId))
            .post("/saved-searches").then().statusCode(200).extract().path("id");

        RestAssured.given()
            .delete("/saved-searches/" + id)
            .then()
            .statusCode(204);

        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/saved-searches")
            .then()
            .statusCode(200)
            .body("savedSearchList", hasSize(0));
    }
}
