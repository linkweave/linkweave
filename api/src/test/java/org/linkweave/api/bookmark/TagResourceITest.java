package org.linkweave.api.bookmark;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TagResourceITest {


    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenGetTagsNotAuthenticated() {
        RestAssured.given()
            .queryParam("collectionId", UUID.randomUUID().toString())
            .get("/tags")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnEmptyList_whenCollectionHasNoTags() {
        Collection collection = fixtureService.createTestCollection();
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/tags")
            .then()
            .statusCode(200)
            .body("tagList", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldCreateTag_withAutoAssignedColor() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"My Tag"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/tags")
            .then()
            .statusCode(200)
            .body("data.name", equalTo("My Tag"))
            .body("data.color", notNullValue())
            .body("data.collectionId", equalTo(collectionId));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldCreateTag_withProvidedColor() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Colored Tag","color":"#ff0000"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/tags")
            .then()
            .statusCode(200)
            .body("data.color", equalTo("#ff0000"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectDuplicateTagName_withinSameCollection() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","name":"Duplicate"}
            """.formatted(collectionId);

        RestAssured.given().contentType(ContentType.JSON).body(body).post("/tags");

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/tags")
            .then()
            .statusCode(400)
            .body("violations[0].message", equalTo("A tag with this name already exists in the collection."));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldAllowSameTagName_inDifferentCollections() {
        Collection collection1 = fixtureService.createTestCollection();
        Collection collection2 = fixtureService.createTestCollection();

        String body1 = """
            {"collectionId":"%s","name":"Same Name"}
            """.formatted(collection1.getId().getUUID().toString());

        String body2 = """
            {"collectionId":"%s","name":"Same Name"}
            """.formatted(collection2.getId().getUUID().toString());

        RestAssured.given().contentType(ContentType.JSON).body(body1).post("/tags")
            .then().statusCode(200);

        RestAssured.given().contentType(ContentType.JSON).body(body2).post("/tags")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnTags_forCollection() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"Tag1\"}".formatted(collectionId))
            .post("/tags");
        RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"Tag2\"}".formatted(collectionId))
            .post("/tags");

        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/tags")
            .then()
            .statusCode(200)
            .body("tagList", hasSize(2));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldUpdateTagName() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String createBody = """
            {"collectionId":"%s","name":"Original"}
            """.formatted(collectionId);

        String tagId = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .post("/tags")
            .then()
            .statusCode(200)
            .extract().path("id");

        String updateBody = """
            {"collectionId":"%s","name":"Updated"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .put("/tags/" + tagId)
            .then()
            .statusCode(200)
            .body("data.name", equalTo("Updated"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldUpdateTagColor() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String createBody = """
            {"collectionId":"%s","name":"ColorTag","color":"#111111"}
            """.formatted(collectionId);

        String tagId = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .post("/tags")
            .then()
            .statusCode(200)
            .extract().path("id");

        String updateBody = """
            {"collectionId":"%s","name":"ColorTag","color":"#222222"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .put("/tags/" + tagId)
            .then()
            .statusCode(200)
            .body("data.color", equalTo("#222222"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDeleteTag() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String createBody = """
            {"collectionId":"%s","name":"ToDelete"}
            """.formatted(collectionId);

        String tagId = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .post("/tags")
            .then()
            .statusCode(200)
            .extract().path("id");

        RestAssured.given()
            .delete("/tags/" + tagId)
            .then()
            .statusCode(204);

        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/tags")
            .then()
            .statusCode(200)
            .body("tagList", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDeleteTagAndPreserveBookmarks() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String tagId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"Preserve\"}".formatted(collectionId))
            .post("/tags").then().statusCode(200).extract().path("id");

        RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"BM\",\"url\":\"https://x.com\",\"tagIds\":[\"%s\"]}".formatted(collectionId, tagId))
            .post("/bookmarks").then().statusCode(200);

        RestAssured.given()
            .delete("/tags/" + tagId)
            .then()
            .statusCode(204);

        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList", hasSize(1));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDeleteTagFromAllBookmarks() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String tagId = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"DeleteMe\"}".formatted(collectionId))
            .post("/tags").then().statusCode(200).extract().path("id");

        RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"title\":\"BM\",\"url\":\"https://x.com\",\"tagIds\":[\"%s\"]}".formatted(collectionId, tagId))
            .post("/bookmarks").then().statusCode(200);

        RestAssured.given()
            .delete("/tags/" + tagId)
            .then()
            .statusCode(204);

        RestAssured.given()
            .queryParam("collectionId", collectionId)
            .get("/bookmarks")
            .then()
            .statusCode(200)
            .body("bookmarkList[0].data.tagIds", not(hasItem(tagId)));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenGetTagsWithoutCollectionAccess() {
        RestAssured.given()
            .queryParam("collectionId", UUID.randomUUID().toString())
            .get("/tags")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldAutoAssignColors_fromPalette() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String color1 = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"T1\"}".formatted(collectionId))
            .post("/tags").then().statusCode(200).extract().path("data.color");

        String color2 = RestAssured.given().contentType(ContentType.JSON)
            .body("{\"collectionId\":\"%s\",\"name\":\"T2\"}".formatted(collectionId))
            .post("/tags").then().statusCode(200).extract().path("data.color");

        assertThat(color1, not(equalTo(color2)));
    }
}
