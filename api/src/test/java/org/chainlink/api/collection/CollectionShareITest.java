package org.chainlink.api.collection;

import java.util.UUID;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.shared.user.User;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionShareITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    UserRepo userRepo;

    String collectionId;

    private static final String OWNER = "test@example.com";
    private static final String ALICE = "alice@example.com";

    @Test
    @Order(1)
    void shouldReturn401_whenListMembersNotAuthenticated() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .get("/collections/{id}/members")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(2)
    void shouldReturn401_whenShareNotAuthenticated() {
        String body = """
            {"email":"someone@example.com"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", UUID.randomUUID().toString())
            .post("/collections/{id}/members")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(3)
    void shouldReturn401_whenRevokeNotAuthenticated() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .pathParam("userId", UUID.randomUUID().toString())
            .delete("/collections/{id}/members/{userId}")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(4)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldCreateCollectionForSubsequentTests() {
        var col = fixtureService.createTestCollection();
        collectionId = col.getId().getUUID().toString();
    }

    @Test
    @Order(5)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldListMembersWithOwner() {
        RestAssured.given()
            .pathParam("id", collectionId)
            .get("/collections/{id}/members")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].email", equalTo(OWNER))
            .body("[0].role", equalTo("OWNER"))
            .body("[0].displayName", notNullValue())
            .body("[0].userId", notNullValue());
    }

    @Test
    @Order(6)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldShareWithExistingUser() {
        User alice = userRepo.findByEmail(EmailAddress.fromString(ALICE)).orElseThrow();
        String aliceId = alice.getId().getUUID().toString();

        String body = """
            {"email":"alice@example.com"}
            """;

        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .post("/collections/{id}/members")
            .then()
            .statusCode(200)
            .body("email", equalTo(ALICE))
            .body("displayName", equalTo("Alice User"))
            .body("role", equalTo("MEMBER"))
            .body("userId", equalTo(aliceId));

        RestAssured.given()
            .pathParam("id", collectionId)
            .get("/collections/{id}/members")
            .then()
            .statusCode(200)
            .body("size()", equalTo(2));
    }

    @Test
    @Order(7)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldReturnSharedTrueInCollectionListAfterSharing() {
        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("find { it.id == '" + collectionId + "' }.shared", equalTo(true));
    }

    @Test
    @Order(8)
    @TestSecurity(user = ALICE, roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenListMembersAsMember() {
        RestAssured.given()
            .pathParam("id", collectionId)
            .get("/collections/{id}/members")
            .then()
            .statusCode(403);
    }

    @Test
    @Order(9)
    @TestSecurity(user = ALICE, roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenShareAsMember() {
        String body = """
            {"email":"someone@example.com"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .post("/collections/{id}/members")
            .then()
            .statusCode(403);
    }

    @Test
    @Order(10)
    @TestSecurity(user = ALICE, roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenRevokeAsMember() {
        User alice = userRepo.findByEmail(EmailAddress.fromString(ALICE)).orElseThrow();
        RestAssured.given()
            .pathParam("id", collectionId)
            .pathParam("userId", alice.getId().getUUID().toString())
            .delete("/collections/{id}/members/{userId}")
            .then()
            .statusCode(403);
    }

    @Test
    @Order(11)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldReturnError_whenShareWithSelf() {
        String body = """
            {"email":"test@example.com"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .post("/collections/{id}/members")
            .then()
            .statusCode(400)
            .body("violations[0].key", equalTo("ShareCannotShareWithSelf"));
    }

    @Test
    @Order(12)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldReturnError_whenShareWithNonexistentUser() {
        String body = """
            {"email":"nonexistent@example.com"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .post("/collections/{id}/members")
            .then()
            .statusCode(400)
            .body("violations[0].key", equalTo("ShareUserNotFound"));
    }

    @Test
    @Order(13)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldReturnError_whenShareWithUserWhoAlreadyHasAccess() {
        String body = """
            {"email":"alice@example.com"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .post("/collections/{id}/members")
            .then()
            .statusCode(400)
            .body("violations[0].key", equalTo("ShareAlreadyHasAccess"));
    }

    @Test
    @Order(14)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldRevokeMemberAccess() {
        User alice = userRepo.findByEmail(EmailAddress.fromString(ALICE)).orElseThrow();
        RestAssured.given()
            .pathParam("id", collectionId)
            .pathParam("userId", alice.getId().getUUID().toString())
            .delete("/collections/{id}/members/{userId}")
            .then()
            .statusCode(204);
    }

    @Test
    @Order(15)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldListSingleMemberAfterRevoking() {
        RestAssured.given()
            .pathParam("id", collectionId)
            .get("/collections/{id}/members")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].role", equalTo("OWNER"));
    }

    @Test
    @Order(16)
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenShareWithInvalidEmail() {
        String body = """
            {"email":"not-an-email"}
            """;
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId)
            .post("/collections/{id}/members")
            .then()
            .statusCode(400);
    }
}
