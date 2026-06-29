package org.linkweave.api.auth;

import java.util.UUID;

import org.linkweave.api.shared.auth.Permission;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class FormLoginITest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void shouldRegisterAndLoginAndGetMe() throws Exception {
        String email = "e2e-" + UUID.randomUUID() + "@test.com";
        String password = "e2e-password-123";

        registerUser(email, password);

        String sessionCookie = loginAndGetCookie(email, password);
        assertThat(sessionCookie).isNotBlank();

        String meJson = RestAssured.given()
            .cookie("linkweave-credential", sessionCookie)
            .get("/auth/me")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        UserInfoJson userInfo = objectMapper.readValue(meJson, UserInfoJson.class);
        assertThat(userInfo.email()).isEqualTo(email);
        assertThat(userInfo.firstName()).isEqualTo("E2E");
        assertThat(userInfo.lastName()).isEqualTo("Tester");
        assertThat(userInfo.permissions()).contains(Permission.BOOKMARK_READ, Permission.BOOKMARK_WRITE);
        assertThat(userInfo.defaultCollectionId()).isNotNull();
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        // ARRANGE
        String email = "wrong-" + UUID.randomUUID() + "@test.com";
        registerUser(email, "correct-password");

        // ACT
        RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", email)
            .formParam("j_password", "wrong-password")
            .post("/j_security_check")
            // ASSERT
            .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectLoginForNonexistentUser() {
        RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "nonexistent-" + UUID.randomUUID() + "@test.com")
            .formParam("j_password", "does-not-matter")
            .post("/j_security_check")
            .then()
            .statusCode(401);
    }

    @Test
    void shouldLogoutAndLoseAccess() {
        String email = "logout-" + UUID.randomUUID() + "@test.com";
        String password = "logout-password";
        registerUser(email, password);

        String sessionCookie = loginAndGetCookie(email, password);

        RestAssured.given()
            .cookie("linkweave-credential", sessionCookie)
            .get("/auth/me")
            .then()
            .statusCode(200);

        var logoutResponse = RestAssured.given()
            .cookie("linkweave-credential", sessionCookie)
            .post("/auth/logout")
            .then()
            .statusCode(204)
            .extract();

        String clearedCookie = logoutResponse.cookie("linkweave-credential");
        assertThat(clearedCookie).isEmpty();

        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectMeWithoutLogin() {
        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(401);
    }

    @Test
    void shouldRegisterThenLoginThenLogoutThenLoginAgain() {
        String email = "multi-" + UUID.randomUUID() + "@test.com";
        String password = "multi-password";
        registerUser(email, password);

        String cookie1 = loginAndGetCookie(email, password);
        assertThat(cookie1).isNotBlank();

        RestAssured.given()
            .cookie("linkweave-credential", cookie1)
            .post("/auth/logout")
            .then()
            .statusCode(204);

        String cookie2 = loginAndGetCookie(email, password);
        assertThat(cookie2).isNotBlank();

        RestAssured.given()
            .cookie("linkweave-credential", cookie2)
            .get("/auth/me")
            .then()
            .statusCode(200);
    }

    @Test
    void shouldGetCorrectRolesFromFormLogin() throws Exception {
        // ARRANGE
        String email = "roles-" + UUID.randomUUID() + "@test.com";
        String password = "roles-password";
        registerUser(email, password);

        String cookie = loginAndGetCookie(email, password);

        // ACT
        String meJson = RestAssured.given()
            .cookie("linkweave-credential", cookie)
            .get("/auth/me")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        // ASSERT
        UserInfoJson userInfo = objectMapper.readValue(meJson, UserInfoJson.class);
        assertThat(userInfo.permissions()).contains(Permission.BOOKMARK_READ, Permission.BOOKMARK_WRITE);
    }

    private void registerUser(String email, String password) {
        String body = """
            {
                "email": "%s",
                "password": "%s",
                "vorname": "E2E",
                "nachname": "Tester"
            }
            """.formatted(email, password);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/auth/register")
            .then()
            .statusCode(200);
    }

    private String loginAndGetCookie(String email, String password) {
        return RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", email)
            .formParam("j_password", password)
            .post("/j_security_check")
            .then()
            .statusCode(200)
            .extract()
            .cookie("linkweave-credential");
    }
}
