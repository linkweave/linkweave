package org.chainlink.api.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AuthFlowITest {

    @Test
    void shouldRejectUnauthenticatedMeRequest() {
        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectLoginWithBadCredentials() {
        RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "test@example.com")
            .formParam("j_password", "wrong-password")
            .post("/j_security_check")
            .then()
            .statusCode(401);
    }

    @Test
    void shouldLoginAndAccessProtectedEndpoint() {
        String sessionCookie = RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "test@example.com")
            .formParam("j_password", "test")
            .post("/j_security_check")
            .then()
            .statusCode(200)
            .extract()
            .cookie("quarkus-credential");

        assertThat(sessionCookie).isNotBlank();

        UserInfo userInfo = RestAssured.given()
            .cookie("quarkus-credential", sessionCookie)
            .get("/auth/me")
            .then()
            .statusCode(200)
            .extract()
            .as(UserInfo.class);

        assertThat(userInfo.email()).isEqualTo("test@example.com");
        assertThat(userInfo.roles()).contains("USER");
    }

    @Test
    void shouldLogoutAndLoseAccess() {
        String sessionCookie = RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "test@example.com")
            .formParam("j_password", "test")
            .post("/j_security_check")
            .then()
            .statusCode(200)
            .extract()
            .cookie("quarkus-credential");

        assertThat(sessionCookie).isNotBlank();

        RestAssured.given()
            .cookie("quarkus-credential", sessionCookie)
            .get("/auth/me")
            .then()
            .statusCode(200);

        var logoutResponse = RestAssured.given()
            .cookie("quarkus-credential", sessionCookie)
            .post("/auth/logout")
            .then()
            .statusCode(204)
            .extract();

        String clearedCookie = logoutResponse.cookie("quarkus-credential");
        assertThat(clearedCookie).isEmpty();

        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(401);
    }
}
