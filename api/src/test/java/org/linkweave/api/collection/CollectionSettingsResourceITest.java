package org.linkweave.api.collection;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class CollectionSettingsResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRoundTripSortPreferenceViaSettingsEndpoint() {
        Collection collection = fixtureService.createTestCollection();
        String id = collection.getId().getUUID().toString();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .pathParam("id", id)
            .body("{\"sortField\":\"TITLE\",\"sortDirection\":\"ASC\"}")
            .put("/collections/{id}/settings")
            .then()
            .statusCode(200)
            .body("sortField", equalTo("TITLE"))
            .body("sortDirection", equalTo("ASC"));

        RestAssured.given()
            .pathParam("id", id)
            .get("/collections/{id}/settings")
            .then()
            .statusCode(200)
            .body("sortField", equalTo("TITLE"))
            .body("sortDirection", equalTo("ASC"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403WhenAccessingAnotherUsersCollectionSettings() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .get("/collections/{id}/settings")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldResetSortPreferenceViaDeleteEndpoint_keepingLayout() {
        Collection collection = fixtureService.createTestCollection();
        String id = collection.getId().getUUID().toString();

        RestAssured.given()
            .contentType(ContentType.JSON)
            .pathParam("id", id)
            .body("{\"layout\":\"grid\",\"sortField\":\"CLICK_COUNT\",\"sortDirection\":\"DESC\"}")
            .put("/collections/{id}/settings")
            .then()
            .statusCode(200);

        RestAssured.given()
            .pathParam("id", id)
            .delete("/collections/{id}/settings/sort")
            .then()
            .statusCode(204);

        RestAssured.given()
            .pathParam("id", id)
            .get("/collections/{id}/settings")
            .then()
            .statusCode(200)
            .body("layout", equalTo("grid"))
            .body("sortField", nullValue())
            .body("sortDirection", nullValue());
    }
}
