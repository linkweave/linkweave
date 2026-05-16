package org.chainlink.api.bookmark.property;

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
class PropertyDefinitionResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        RestAssured.given()
            .queryParam("collectionId", "00000000-0000-0000-0000-000000000000")
            .get("/property-definitions")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnEmptyList_whenCollectionHasNoDefinitions() {
        Collection collection = fixtureService.createTestCollection();
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/property-definitions")
            .then()
            .statusCode(200)
            .body("propertyDefinitions", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldListExistingDefinitions() {
        Collection collection = fixtureService.createTestCollection();
        fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Priority")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );
        fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Reviewed")
            .withType(PropertyType.BOOLEAN)
            .withSortOrder(1)
        );

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/property-definitions")
            .then()
            .statusCode(200)
            .body("propertyDefinitions", hasSize(2))
            .body("propertyDefinitions[0].data.name", equalTo("Priority"))
            .body("propertyDefinitions[1].data.name", equalTo("Reviewed"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldCreateDefinition() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Priority","type":"TEXT","sortOrder":0}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            .then()
            .statusCode(200)
            .body("data.name", equalTo("Priority"))
            .body("data.type", equalTo("TEXT"))
            .body("data.collectionId", equalTo(collectionId));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectCreate_whenNameBlank() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"","type":"TEXT","sortOrder":0}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldUpdateDefinition() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("OldName")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );

        String body = """
            {"collectionId":"%s","name":"NewName","type":"NUMBER","sortOrder":3}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/property-definitions/" + def.getId().getUUID())
            .then()
            .statusCode(200)
            .body("data.name", equalTo("NewName"))
            .body("data.type", equalTo("NUMBER"))
            .body("data.sortOrder", equalTo(3));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDeleteDefinition() {
        Collection collection = fixtureService.createTestCollection();
        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Temp")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );

        RestAssured.given()
            .delete("/property-definitions/" + def.getId().getUUID())
            .then()
            .statusCode(204);

        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/property-definitions")
            .then()
            .statusCode(200)
            .body("propertyDefinitions", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenListingCollectionWithoutAccess() {
        RestAssured.given()
            .queryParam("collectionId", java.util.UUID.randomUUID().toString())
            .get("/property-definitions")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturn403_whenCreatingInCollectionWithoutAccess() {
        String body = """
            {"collectionId":"%s","name":"Sneaky","type":"TEXT","sortOrder":0}
            """.formatted(java.util.UUID.randomUUID());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            .then()
            .statusCode(403);
    }
}
