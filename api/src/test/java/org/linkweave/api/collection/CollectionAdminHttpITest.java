package org.linkweave.api.collection;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.emailaddress.EmailAddress;

import static org.hamcrest.Matchers.equalTo;

/**
 * HTTP-layer tests for the ADMIN collection role: verifies the resource-layer
 * guards (requireAdminOrOwner / requireOwnerAccess) admit admins to management
 * endpoints while still blocking delete/rename.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionAdminHttpITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    UserRepo userRepo;

    String collectionId;

    private static final String OWNER = "test@example.com";
    private static final String ADMIN = "alice@example.com";

    @Test
    @Order(1)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldCreateCollectionAndInviteAdmin() {
        // ARRANGE
        var col = fixtureService.createTestCollection();
        collectionId = col.getId().getUUID().toString();
        String body = """
            {"email":"alice@example.com", "role":"ADMIN"}
            """;

        // ACT — owner invites alice directly as ADMIN
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .post("/collections/{id}/members")
            // ASSERT
            .then()
            .statusCode(200)
            .body("role", equalTo("ADMIN"));
    }

    @Test
    @Order(2)
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_READ"})
    void shouldLetAdminListMembers() {
        RestAssured.given()
            .pathParam("id", collectionId)
            .get("/collections/{id}/members")
            .then()
            .statusCode(200)
            .body("members.size()", equalTo(2));
    }

    @Test
    @Order(3)
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_READ"})
    void shouldRejectAdminDeletingCollection() {
        RestAssured.given()
            .pathParam("id", collectionId)
            .delete("/collections/{id}")
            .then()
            .statusCode(403);
    }

    @Test
    @Order(4)
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_READ"})
    void shouldLetAdminUpdateConfigButPreserveName() {
        // ARRANGE
        String originalName = RestAssured.given()
            .pathParam("id", collectionId)
            .get("/collections/{id}")
            .then()
            .extract().jsonPath().getString("name");
        String body = """
            {"name":"Hacked Name", "browserFetchAllowlist":"example.com", "screenshotEnabled":true}
            """;

        // ACT — admin updates config; name must be ignored
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .put("/collections/{id}")
            // ASSERT
            .then()
            .statusCode(200)
            .body("name", equalTo(originalName));
    }

    @Test
    @Order(5)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldLetOwnerDemoteAdminViaHttp() {
        // ARRANGE
        User alice = userRepo.findByEmail(EmailAddress.fromString(ADMIN)).orElseThrow();
        String body = """
            {"role":"MEMBER"}
            """;

        // ACT
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .pathParam("userId", alice.getId().getUUID().toString())
            .put("/collections/{id}/members/{userId}")
            // ASSERT
            .then()
            .statusCode(200)
            .body("role", equalTo("MEMBER"));
    }

    @Test
    @Order(6)
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_READ"})
    void shouldRejectDemotedMemberListingMembers() {
        // alice was demoted to MEMBER in the previous step
        RestAssured.given()
            .pathParam("id", collectionId)
            .get("/collections/{id}/members")
            .then()
            .statusCode(403);
    }
}
