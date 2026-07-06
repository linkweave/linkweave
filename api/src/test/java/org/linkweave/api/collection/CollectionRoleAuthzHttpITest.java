package org.linkweave.api.collection;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.emailaddress.EmailAddress;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.db.DatabaseService;

import static org.hamcrest.Matchers.equalTo;

/**
 * HTTP-layer tests for the caller-authorization rules that the resource layer
 * enforces on member management: only the owner may grant/revoke/change the
 * ADMIN role, an admin may only step *themselves* down, and OWNER can never be
 * assigned. These map to 403 (authorization) or 400 (invariant), never 500.
 */
@QuarkusTest
class CollectionRoleAuthzHttpITest {

    private static final String OWNER = "test@example.com";
    private static final String ADMIN = "alice@example.com";

    @Inject
    CollectionService collectionService;

    @Inject
    UserRepo userRepo;

    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    private User seeded(String email) {
        return userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow();
    }

    /** Owner (seeded "test") owns a collection on which "alice" is an ADMIN. */
    private ID<Collection> collectionWithAliceAdmin() {
        User ownerUser = seeded(OWNER);
        User adminUser = seeded(ADMIN);
        ID<Collection> cid = collectionService.createCollection("Shared", ownerUser).getId();
        collectionService.shareWithUser(cid, adminUser.getEmail(), CollectionRole.ADMIN, ownerUser);
        return cid;
    }

    private User memberOf(ID<Collection> collectionId, CollectionRole role) {
        User target = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@target.com").withVorname("T").withNachname("User"));
        collectionService.shareWithUser(collectionId, target.getEmail(), role, seeded(OWNER));
        return target;
    }

    @Test
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_WRITE"})
    void shouldRejectAdminInvitingAnotherAdmin() {
        // ARRANGE
        var collectionId = collectionWithAliceAdmin();
        String body = """
            {"email":"nobody@example.com", "role":"ADMIN"}
            """;

        // ACT
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId.getUUID().toString())
            // ASSERT — only the owner may grant ADMIN
            .post("/collections/{id}/members")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_WRITE"})
    void shouldLetAdminInviteMember() {
        // ARRANGE
        var collectionId = collectionWithAliceAdmin();
        var invitee = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@invitee.com").withVorname("I").withNachname("User"));
        String body = """
            {"email":"%s", "role":"MEMBER"}
            """.formatted(invitee.getEmail().getAddress());

        // ACT
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId.getUUID().toString())
            // ASSERT
            .post("/collections/{id}/members")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_WRITE"})
    void shouldRejectAdminRevokingAnotherAdmin() {
        // ARRANGE
        var collectionId = collectionWithAliceAdmin();
        var otherAdmin = memberOf(collectionId, CollectionRole.ADMIN);

        // ACT
        RestAssured.given()
            .pathParam("id", collectionId.getUUID().toString())
            .pathParam("userId", otherAdmin.getId().getUUID().toString())
            // ASSERT — an admin may not remove another admin
            .delete("/collections/{id}/members/{userId}")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_WRITE"})
    void shouldLetAdminRevokeMember() {
        // ARRANGE
        var collectionId = collectionWithAliceAdmin();
        var plainMember = memberOf(collectionId, CollectionRole.MEMBER);

        // ACT
        RestAssured.given()
            .pathParam("id", collectionId.getUUID().toString())
            .pathParam("userId", plainMember.getId().getUUID().toString())
            // ASSERT
            .delete("/collections/{id}/members/{userId}")
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_WRITE"})
    void shouldRejectAdminPromotingAnotherMember() {
        // ARRANGE
        var collectionId = collectionWithAliceAdmin();
        var plainMember = memberOf(collectionId, CollectionRole.MEMBER);
        String body = """
            {"role":"ADMIN"}
            """;

        // ACT
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId.getUUID().toString())
            .pathParam("userId", plainMember.getId().getUUID().toString())
            // ASSERT — only the owner may change another member's role
            .put("/collections/{id}/members/{userId}")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = ADMIN, roles = {"BOOKMARK_WRITE"})
    void shouldLetAdminStepSelfDown() {
        // ARRANGE
        var collectionId = collectionWithAliceAdmin();
        var alice = seeded(ADMIN);
        String body = """
            {"role":"MEMBER"}
            """;

        // ACT
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId.getUUID().toString())
            .pathParam("userId", alice.getId().getUUID().toString())
            // ASSERT — an admin may step themselves down to member
            .put("/collections/{id}/members/{userId}")
            .then()
            .statusCode(200)
            .body("role", equalTo("MEMBER"));
    }

    @Test
    @TestSecurity(user = OWNER, roles = {"BOOKMARK_WRITE"})
    void shouldRejectAssigningOwnerRoleViaInvite() {
        // ARRANGE — even the owner cannot mint a second owner
        var collectionId = collectionWithAliceAdmin();
        var invitee = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@invitee.com").withVorname("I").withNachname("User"));
        String body = """
            {"email":"%s", "role":"OWNER"}
            """.formatted(invitee.getEmail().getAddress());

        // ACT
        RestAssured.given()
            .contentType("application/json")
            .body(body)
            .pathParam("id", collectionId.getUUID().toString())
            // ASSERT
            .post("/collections/{id}/members")
            .then()
            .statusCode(400);
    }
}
