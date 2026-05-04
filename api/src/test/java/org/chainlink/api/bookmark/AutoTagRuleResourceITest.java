package org.chainlink.api.bookmark;

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
class AutoTagRuleResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnEmptyList_whenCollectionHasNoRules() {
        Collection collection = fixtureService.createTestCollection();
        RestAssured.given()
            .queryParam("collectionId", collection.getId().getUUID().toString())
            .get("/auto-tag-rules")
            .then()
            .statusCode(200)
            .body("rules", hasSize(0));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldCreateRuleAndNormaliseTagNames() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","pattern":"^https://github\\\\.com/.+/pull/\\\\d+","tagNames":"PR, github, pr","enabled":true}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auto-tag-rules")
            .then()
            .statusCode(200)
            .body("data.tagNames", equalTo("pr,github"))
            .body("data.enabled", equalTo(true))
            .body("data.collectionId", equalTo(collectionId));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectInvalidRegex() {
        Collection collection = fixtureService.createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","pattern":"[unclosed","tagNames":"foo","enabled":true}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auto-tag-rules")
            .then()
            .statusCode(400);
    }
}
