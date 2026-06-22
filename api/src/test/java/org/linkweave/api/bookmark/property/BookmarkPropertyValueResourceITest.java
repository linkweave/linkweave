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

@QuarkusTest
class BookmarkPropertyValueResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"propertyValues\":[]}")
            .put("/bookmarks/00000000-0000-0000-0000-000000000000/properties")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldSetTextPropertyOnBookmark() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Priority")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );
        String definitionId = def.getId().getUUID().toString();

        String bookmarkId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"My Bookmark\",\"url\":\"https://example.com\"}".formatted(collectionId))
            .post("/bookmarks")
            .then().statusCode(200)
            .extract().path("id");

        String body = """
            {"propertyValues":[{"definitionId":"%s","valueText":"High"}]}
            """.formatted(definitionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then()
            .statusCode(200)
            .body("id", equalTo(bookmarkId));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldSetBooleanPropertyOnBookmark() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Reviewed")
            .withType(PropertyType.BOOLEAN)
            .withSortOrder(0)
        );
        String definitionId = def.getId().getUUID().toString();

        String bookmarkId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"BM\",\"url\":\"https://example.com\"}".formatted(collectionId))
            .post("/bookmarks")
            .then().statusCode(200)
            .extract().path("id");

        String body = """
            {"propertyValues":[{"definitionId":"%s","valueBoolean":true}]}
            """.formatted(definitionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldSetNumberPropertyOnBookmark() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Rating")
            .withType(PropertyType.NUMBER)
            .withSortOrder(0)
        );
        String definitionId = def.getId().getUUID().toString();

        String bookmarkId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"BM\",\"url\":\"https://example.com\"}".formatted(collectionId))
            .post("/bookmarks")
            .then().statusCode(200)
            .extract().path("id");

        String body = """
            {"propertyValues":[{"definitionId":"%s","valueNumber":4.5}]}
            """.formatted(definitionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldSetMultiplePropertiesOnBookmark() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        PropertyDefinition textDef = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Status")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );
        PropertyDefinition boolDef = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Active")
            .withType(PropertyType.BOOLEAN)
            .withSortOrder(1)
        );

        String bookmarkId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"BM\",\"url\":\"https://example.com\"}".formatted(collectionId))
            .post("/bookmarks")
            .then().statusCode(200)
            .extract().path("id");

        String body = """
            {"propertyValues":[
                {"definitionId":"%s","valueText":"In Progress"},
                {"definitionId":"%s","valueBoolean":true}
            ]}
            """.formatted(textDef.getId().getUUID().toString(), boolDef.getId().getUUID().toString());

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReplaceExistingProperties_whenPuttingNewOnes() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Note")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );
        String definitionId = def.getId().getUUID().toString();

        String bookmarkId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"BM\",\"url\":\"https://example.com\"}".formatted(collectionId))
            .post("/bookmarks")
            .then().statusCode(200)
            .extract().path("id");

        String firstBody = """
            {"propertyValues":[{"definitionId":"%s","valueText":"Old Value"}]}
            """.formatted(definitionId);
        RestAssured.given().contentType(ContentType.JSON)
            .body(firstBody)
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then().statusCode(200);

        String secondBody = """
            {"propertyValues":[{"definitionId":"%s","valueText":"New Value"}]}
            """.formatted(definitionId);
        RestAssured.given().contentType(ContentType.JSON)
            .body(secondBody)
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldClearProperties_whenPuttingEmptyList() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        PropertyDefinition def = fixtureService.persistPropertyDefinition(b -> b
            .withCollection(collection)
            .withName("Temp")
            .withType(PropertyType.TEXT)
            .withSortOrder(0)
        );
        String definitionId = def.getId().getUUID().toString();

        String bookmarkId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"BM\",\"url\":\"https://example.com\"}".formatted(collectionId))
            .post("/bookmarks")
            .then().statusCode(200)
            .extract().path("id");

        String setBody = """
            {"propertyValues":[{"definitionId":"%s","valueText":"To be cleared"}]}
            """.formatted(definitionId);
        RestAssured.given().contentType(ContentType.JSON)
            .body(setBody)
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then().statusCode(200);

        RestAssured.given().contentType(ContentType.JSON)
            .body("{\"propertyValues\":[]}")
            .put("/bookmarks/" + bookmarkId + "/properties")
            .then().statusCode(200);
    }
}
