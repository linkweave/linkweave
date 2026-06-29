package org.linkweave.api.bookmark.property;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
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
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        // ACT
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/property-definitions")
            // ASSERT
            .then()
            .statusCode(200)
            .body("propertyDefinitions", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldListExistingDefinitions() {
        // ARRANGE
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

        // ACT
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/property-definitions")
            // ASSERT
            .then()
            .statusCode(200)
            .body("propertyDefinitions", hasSize(2))
            .body("propertyDefinitions[0].data.name", equalTo("Priority"))
            .body("propertyDefinitions[1].data.name", equalTo("Reviewed"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldCreateDefinition() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Priority","type":"TEXT","sortOrder":0}
            """.formatted(collectionId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            // ASSERT
            .then()
            .statusCode(200)
            .body("data.name", equalTo("Priority"))
            .body("data.type", equalTo("TEXT"))
            .body("data.collectionId", equalTo(collectionId));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectCreate_whenNameBlank() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"","type":"TEXT","sortOrder":0}
            """.formatted(collectionId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldUpdateDefinition() {
        // ARRANGE
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

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/property-definitions/" + def.getId().getUUID())
            // ASSERT
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
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDeleteDefinition_andCascadeRemoveValuesFromBookmarks() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();
        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Priority")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );
        String definitionId = def.getId().getUUID().toString();

        // Two bookmarks both carry a value for this property.
        for (int i = 0; i < 2; i++) {
            String bookmarkId = RestAssured.given().contentType(ContentType.JSON)
                .body("{\"collectionId\":\"%s\",\"title\":\"BM%d\",\"url\":\"https://example.com/%d\"}".formatted(collectionId, i, i))
                .post("/bookmarks")
                .then().statusCode(200)
                .extract().path("id");
            String body = """
                {"propertyValues":[{"definitionId":"%s","valueText":"High"}]}
                """.formatted(definitionId);
            RestAssured.given().contentType(ContentType.JSON).body(body)
                .put("/bookmarks/" + bookmarkId + "/properties")
                .then().statusCode(200);
        }

        // Usage endpoint reflects the 2 affected bookmarks.
        RestAssured.given()
            .get("/property-definitions/" + definitionId + "/usage")
            .then()
            .statusCode(200)
            .body("affectedBookmarks", equalTo(2));

        // Delete succeeds despite values existing — previously this failed with an FK violation.
        RestAssured.given()
            .delete("/property-definitions/" + definitionId)
            .then()
            .statusCode(204);

        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/property-definitions")
            .then()
            .statusCode(200)
            .body("propertyDefinitions", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnZeroUsage_whenNoBookmarksUseProperty() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Unused")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );

        // ACT
        RestAssured.given()
            .get("/property-definitions/" + def.getId().getUUID() + "/usage")
            // ASSERT
            .then()
            .statusCode(200)
            .body("affectedBookmarks", equalTo(0));
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
        // ARRANGE
        String body = """
            {"collectionId":"%s","name":"Sneaky","type":"TEXT","sortOrder":0}
            """.formatted(java.util.UUID.randomUUID());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectDuplicatePropertyName_withinSameCollection() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Priority","type":"TEXT","sortOrder":0}
            """.formatted(collectionId);

        // First create succeeds
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            .then()
            .statusCode(200);

        // Duplicate name rejected with 400 + translated message
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/property-definitions")
            .then()
            .statusCode(400)
            .body("violations[0].message", equalTo("A property with this name already exists in the collection."));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectDuplicatePropertyName_onUpdate() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Status")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );
        PropertyDefinition target = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Priority")
            .withType(PropertyType.NUMBER)
            .withSortOrder(1)
        );

        // Try to rename "Priority" to "Status" — conflicts with existing
        String body = """
            {"collectionId":"%s","name":"Status","type":"NUMBER","sortOrder":1}
            """.formatted(collectionId);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/property-definitions/" + target.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(400)
            .body("violations[0].message", equalTo("A property with this name already exists in the collection."));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldAllowSamePropertyName_inDifferentCollections() {
        Collection collection1 = fixtureService.createTestCollection();
        Collection collection2 = fixtureService.createTestCollection();

        String body1 = """
            {"collectionId":"%s","name":"Priority","type":"TEXT","sortOrder":0}
            """.formatted(collection1.getId().getUUID().toString());

        String body2 = """
            {"collectionId":"%s","name":"Priority","type":"NUMBER","sortOrder":0}
            """.formatted(collection2.getId().getUUID().toString());

        RestAssured.given().contentType(ContentType.JSON).body(body1).post("/property-definitions")
            .then().statusCode(200);

        RestAssured.given().contentType(ContentType.JSON).body(body2).post("/property-definitions")
            .then().statusCode(200)
            .body("data.name", equalTo("Priority"))
            .body("data.type", equalTo("NUMBER"));
    }
}
