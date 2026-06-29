package org.linkweave.api.auth;

import java.util.UUID;

import org.linkweave.api.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.user.AuthProvider;
import org.linkweave.api.shared.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RegistrationResourceITest {

    @Inject
    UserRepo userRepo;

    @Test
    void shouldRegisterViaApiAndCreateUserInDatabase() {
        // ARRANGE
        String email = uniqueEmail();
        String body = """
            {
                "email": "%s",
                "password": "secure-password-123",
                "vorname": "Max",
                "nachname": "Mustermann"
            }
            """.formatted(email);

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            .then()
            .statusCode(200);

        // ASSERT
        User user = userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow();
        assertThat(user.getVorname()).isEqualTo("Max");
        assertThat(user.getNachname()).isEqualTo("Mustermann");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.FORM);
        assertThat(user.getPassword()).isNotBlank();
        assertThat(user.getPassword()).startsWith("$2");
    }

    @Test
    void shouldReturnConflictForDuplicateEmail() {
        String email = uniqueEmail();
        String body = """
            {
                "email": "%s",
                "password": "password-12345",
                "vorname": "First",
                "nachname": "User"
            }
            """.formatted(email);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            .then()
            .statusCode(200);

        String duplicateBody = """
            {
                "email": "%s",
                "password": "different-password",
                "vorname": "Second",
                "nachname": "User"
            }
            """.formatted(email);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(duplicateBody)
            .post("/auth/register")
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnBadRequestForMissingEmail() {
        // ARRANGE
        String body = """
            {
                "password": "password-12345",
                "vorname": "Max",
                "nachname": "Mustermann"
            }
            """;

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnBadRequestForMissingPassword() {
        // ARRANGE
        String body = """
            {
                "email": "%s",
                "vorname": "Max",
                "nachname": "Mustermann"
            }
            """.formatted(uniqueEmail());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnBadRequestForShortPassword() {
        // ARRANGE
        String body = """
            {
                "email": "%s",
                "password": "short",
                "vorname": "Max",
                "nachname": "Mustermann"
            }
            """.formatted(uniqueEmail());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnBadRequestForMissingVorname() {
        // ARRANGE
        String body = """
            {
                "email": "%s",
                "password": "password-12345",
                "nachname": "Mustermann"
            }
            """.formatted(uniqueEmail());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnBadRequestForMissingNachname() {
        // ARRANGE
        String body = """
            {
                "email": "%s",
                "password": "password-12345",
                "vorname": "Max"
            }
            """.formatted(uniqueEmail());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    void shouldNotRequireAuthentication() {
        // ARRANGE
        String body = """
            {
                "email": "%s",
                "password": "password-12345",
                "vorname": "No",
                "nachname": "Auth"
            }
            """.formatted(uniqueEmail());

        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            // ASSERT
            .then()
            .statusCode(200);
    }

    private static String uniqueEmail() {
        return "reg-" + UUID.randomUUID() + "@test.com";
    }
}
