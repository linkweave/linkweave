package org.chainlink.api.auth.apikey;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class ApiKeyResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenListNotAuthenticated() {
        RestAssured.given()
            .get("/auth/api-keys")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldCreateApiKey() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Create Test Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .body("name", equalTo("Create Test Key"))
            .body("prefix", notNullValue())
            .body("prefix.length()", greaterThanOrEqualTo(8))
            .body("createdAt", notNullValue())
            .body("expiresAt", nullValue())
            .body("key", startsWith("cl_"))
            .body("key.length()", equalTo(67));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldCreateApiKeyWithExpiration() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Expiring Key\",\"expiresIn\":\"90d\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .body("name", equalTo("Expiring Key"))
            .body("expiresAt", notNullValue())
            .body("key", startsWith("cl_"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldListCreatedKeys() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"List Test Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201);

        RestAssured.given()
            .get("/auth/api-keys")
            .then()
            .statusCode(200)
            .body("apiKeys.name", hasItem(equalTo("List Test Key")));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRevokeApiKey() {
        String id = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Key to Revoke\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("id");

        RestAssured.given()
            .delete("/auth/api-keys/" + id)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenRevokingNonexistentKey() {
        RestAssured.given()
            .delete("/auth/api-keys/" + UUID.randomUUID())
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRejectBlankName() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"   \"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRejectNameTooLong() {
        String longName = "x".repeat(101);
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + longName + "\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldAuthenticateWithApiKey() {
        String fullKey = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Auth Test Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("key");

        RestAssured.given()
            .header("X-API-Key", fullKey)
            .get("/auth/api-keys")
            .then()
            .statusCode(200);
    }

    @Test
    void shouldReturn401_whenInvalidApiKey() {
        RestAssured.given()
            .header("X-API-Key", "cl_" + "a".repeat(64))
            .get("/auth/api-keys")
            .then()
            .statusCode(401);
    }

    @Test
    void shouldReturn401_whenMalformedApiKey() {
        RestAssured.given()
            .header("X-API-Key", "not-a-valid-key")
            .get("/auth/api-keys")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldAuthenticateWithApiKeyAndAccessCollections() {
        String fullKey = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Collection Access Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("key");

        fixtureService.createTestCollection();

        RestAssured.given()
            .header("X-API-Key", fullKey)
            .get("/collections")
            .then()
            .statusCode(200);
    }
}
