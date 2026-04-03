package org.chainlink.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowITest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    @Order(1)
    void shouldRejectUnauthenticatedMeRequest() {
        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(2)
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
    @Order(3)
    void shouldLoginAndAccessProtectedEndpoint() throws Exception {
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

        String json = RestAssured.given()
            .cookie("quarkus-credential", sessionCookie)
            .get("/auth/me")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        UserInfoJson userInfoJson = objectMapper.readValue(json, UserInfoJson.class);

        assertThat(userInfoJson.email()).isEqualTo("test@example.com");
        assertThat(userInfoJson.roles()).contains("USER");
        assertThat(userInfoJson.defaultCollectionId()).isNotNull();
    }

    @Test
    @Order(4)
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
