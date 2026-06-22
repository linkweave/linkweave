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
class CollectionResourceCrudITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenCreateCollectionNotAuthenticated() {
        String body = """
            {"name":"New Collection"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .post("/collections")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldCreateCollection() {
        String uniqueName = "Work_" + UUID.randomUUID();
        String body = """
            {"name":"%s"}
            """.formatted(uniqueName);
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .post("/collections")
            .then()
            .statusCode(200)
            .body("name", equalTo(uniqueName))
            .body("role", equalTo("OWNER"))
            .body("isDefault", equalTo(false))
            .body("id", notNullValue());

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("collections.findAll { it.name == '" + uniqueName + "' }.size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenCreateCollectionWithEmptyName() {
        String body = """
            {"name":""}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .post("/collections")
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn401_whenUpdateCollectionNotAuthenticated() {
        String body = """
            {"name":"Updated"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", UUID.randomUUID().toString())
            .put("/collections/{id}")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenUpdateCollectionWithoutAccess() {
        String body = """
            {"name":"Updated"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", UUID.randomUUID().toString())
            .put("/collections/{id}")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldUpdateCollection_returnsCorrectIsDefault() {
        var col = fixtureService.createTestCollection();
        String colId = col.getId().getUUID().toString();

        // Mark the collection as default first
        RestAssured.given()
            .pathParam("id", colId)
            .put("/collections/{id}/default")
            .then()
            .statusCode(204);

        String newName = "DefaultRenamed_" + UUID.randomUUID();
        String body = """
            {"name":"%s"}
            """.formatted(newName);
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", colId)
            .put("/collections/{id}")
            .then()
            .statusCode(200)
            .body("name", equalTo(newName))
            .body("isDefault", equalTo(true))
            .body("role", equalTo("OWNER"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldUpdateCollection() {
        var col = fixtureService.createTestCollection();
        String colId = col.getId().getUUID().toString();
        String newName = "Renamed_" + UUID.randomUUID();

        String body = """
            {"name":"%s"}
            """.formatted(newName);
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", colId)
            .put("/collections/{id}")
            .then()
            .statusCode(200)
            .body("name", equalTo(newName))
            .body("id", equalTo(colId));

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("collections.find { it.id == '" + colId + "' }.name", equalTo(newName));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenUpdateCollectionWithEmptyName() {
        var col = fixtureService.createTestCollection();
        String body = """
            {"name":""}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", col.getId().getUUID().toString())
            .put("/collections/{id}")
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn401_whenDeleteCollectionNotAuthenticated() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .delete("/collections/{id}")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenDeleteCollectionWithoutAccess() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .delete("/collections/{id}")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldDeleteCollection() {
        var col1 = fixtureService.createTestCollection();
        var owner = col1.getOwner();

        String uniqueName = "ToDelete_" + UUID.randomUUID();
        var col2 = fixtureService.persistCollection(b -> b.withName(uniqueName).withOwner(owner));
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(col2)
            .withUser(owner)
            .withRole(CollectionRole.OWNER)
            .withDefault(false)
        );

        String col2Id = col2.getId().getUUID().toString();

        RestAssured.given()
            .pathParam("id", col2Id)
            .delete("/collections/{id}")
            .then()
            .statusCode(204);

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("collections.findAll { it.id == '" + col2Id + "' }.size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReassignDefault_whenDeletingDefaultCollection() {
        var owner = fixtureService.createTestCollection().getOwner();

        var col2 = fixtureService.persistCollection(b -> b.withName("Second_" + UUID.randomUUID()).withOwner(owner));
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(col2)
            .withUser(owner)
            .withRole(CollectionRole.OWNER)
            .withDefault(true)
        );

        RestAssured.given()
            .pathParam("id", col2.getId().getUUID().toString())
            .delete("/collections/{id}")
            .then()
            .statusCode(204);

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("collections.findAll { it.isDefault == true }.size()", equalTo(1));
    }
}

