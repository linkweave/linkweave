package org.chainlink.api.collection;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class CollectionFaviconAllowlistITest {

    @Inject
    FixtureService fixtureService;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldPersistFaviconAllowlistOnUpdate() {
        var col = fixtureService.createTestCollection();
        String colId = col.getId().getUUID().toString();

        String body = """
            {"name":"With Allowlist","faviconAllowlist":"*.mycompany.domain\\nintranet.local"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", colId)
            .put("/collections/{id}")
            .then()
            .statusCode(200);

        RestAssured.given()
            .pathParam("id", colId)
            .get("/collections/{id}")
            .then()
            .statusCode(200)
            .body("faviconAllowlist", notNullValue())
            .body("faviconAllowlist", equalTo("*.mycompany.domain\nintranet.local"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNormalizeFaviconAllowlist_lowercaseTrimDedupe() {
        var col = fixtureService.createTestCollection();
        String colId = col.getId().getUUID().toString();

        String body = """
            {"name":"Normalize","faviconAllowlist":"  *.MyCompany.Domain  \\n*.mycompany.domain\\nINTRANET.local\\n"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", colId)
            .put("/collections/{id}")
            .then()
            .statusCode(200);

        RestAssured.given()
            .pathParam("id", colId)
            .get("/collections/{id}")
            .then()
            .statusCode(200)
            .body("faviconAllowlist", equalTo("*.mycompany.domain\nintranet.local"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldClearAllowlist_whenEmptyString() {
        var col = fixtureService.createTestCollection();
        String colId = col.getId().getUUID().toString();

        // First set
        RestAssured.given()
            .contentType("application/json")
            .body("""
                {"name":"X","faviconAllowlist":"*.example.com"}
                """)
            .pathParam("id", colId)
            .put("/collections/{id}")
            .then()
            .statusCode(200);

        // Now clear with empty string
        RestAssured.given()
            .contentType("application/json")
            .body("""
                {"name":"X","faviconAllowlist":""}
                """)
            .pathParam("id", colId)
            .put("/collections/{id}")
            .then()
            .statusCode(200);

        RestAssured.given()
            .pathParam("id", colId)
            .get("/collections/{id}")
            .then()
            .statusCode(200)
            .body("faviconAllowlist", nullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenAllowlistContainsBareWildcard() {
        var col = fixtureService.createTestCollection();
        String body = """
            {"name":"X","faviconAllowlist":"*"}
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
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenAllowlistContainsScheme() {
        var col = fixtureService.createTestCollection();
        String body = """
            {"name":"X","faviconAllowlist":"https://example.com"}
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
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenAllowlistContainsPath() {
        var col = fixtureService.createTestCollection();
        String body = """
            {"name":"X","faviconAllowlist":"example.com/foo"}
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
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenAllowlistContainsBareIp() {
        var col = fixtureService.createTestCollection();
        String body = """
            {"name":"X","faviconAllowlist":"10.0.0.1"}
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
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenNonOwnerEditsAllowlist() {
        String body = """
            {"name":"X","faviconAllowlist":"*.example.com"}
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
    void shouldExposeAllowlistInCollectionInfo() {
        var col = fixtureService.createTestCollection();
        String colId = col.getId().getUUID().toString();

        RestAssured.given()
            .contentType("application/json")
            .body("""
                {"name":"With List","faviconAllowlist":"*.mycompany.domain\\nintranet.local"}
                """)
            .pathParam("id", colId)
            .put("/collections/{id}")
            .then()
            .statusCode(200);

        RestAssured.given()
            .pathParam("id", colId)
            .get("/collections/{id}")
            .then()
            .statusCode(200)
            .body("faviconAllowlist", equalTo("*.mycompany.domain\nintranet.local"));
    }
}
