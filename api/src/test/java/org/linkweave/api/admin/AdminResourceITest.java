package org.linkweave.api.admin;

import java.util.UUID;

import org.linkweave.api.auth.RegistrationService;
import org.linkweave.api.types.emailaddress.EmailAddress;
import org.linkweave.api.types.id.ID;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class AdminResourceITest {

    @Inject
    RegistrationService registrationService;

    @Inject
    UserRepo userRepo;

    @Test
    @TestSecurity(user = "alice@example.com", roles = {"SUPPORT"})
    void shouldListAllUsersWhenCallerHasSupportRole() {
        // ARRANGE — alice is the seeded SUPPORT user; register a fresh victim.
        String email = "admin-list-" + UUID.randomUUID() + "@test.com";
        User registered = registrationService.register(email, "password-12345", "Admin", "Testuser");

        // ACT / ASSERT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .get("/admin/users")
            .then()
            .statusCode(200)
            .body("users.email", hasItem(email))
            .body("users.firstName", hasItem("Admin"))
            .body("users.lastName", hasItem("Testuser"))
            .body("users.find { it.email == '" + email + "' }.id", equalTo(registered.getId().getUUID().toString()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldRejectListUsersWithoutSupportRole() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .get("/admin/users")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "alice@example.com", roles = {"SUPPORT"})
    void shouldResetPasswordAndReturnNewPlaintext() {
        // ARRANGE
        String email = "admin-reset-" + UUID.randomUUID() + "@test.com";
        String originalPassword = "original-password-123";
        User registered = registrationService.register(email, originalPassword, "Victim", "User");
        ID<User> userId = registered.getId();
        String hashBefore = userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow().getPassword();

        // ACT
        String newPassword = RestAssured.given()
            .contentType(ContentType.JSON)
            .post("/admin/users/{userId}/reset-password", userId.getUUID())
            .then()
            .statusCode(200)
            .extract()
            .path("newPassword");

        // ASSERT
        assertThat(newPassword).isNotBlank().hasSize(16);

        String hashAfter = userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow().getPassword();
        assertThat(hashAfter).startsWith("$2").isNotEqualTo(hashBefore);

        // The returned plaintext must verify against the new hash,
        // and the old plaintext must no longer match.
        assertThat(BcryptUtil.matches(newPassword, hashAfter)).isTrue();
        assertThat(BcryptUtil.matches(originalPassword, hashAfter)).isFalse();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldRejectResetPasswordWithoutSupportRole() {
        // ARRANGE
        String email = "admin-forbidden-" + UUID.randomUUID() + "@test.com";
        User registered = registrationService.register(email, "password-12345", "Victim", "User");

        // ACT / ASSERT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .post("/admin/users/{userId}/reset-password", registered.getId().getUUID())
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "alice@example.com", roles = {"SUPPORT"})
    void shouldRefuseToResetSystemAdminPassword() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .post("/admin/users/{userId}/reset-password", User.getSystemAdminId().getUUID())
            .then()
            .statusCode(400);
    }
}
