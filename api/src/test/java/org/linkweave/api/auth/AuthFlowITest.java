package org.linkweave.api.auth;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
            .cookie("linkweave-credential");

        assertThat(sessionCookie).isNotBlank();

        String json = RestAssured.given()
            .cookie("linkweave-credential", sessionCookie)
            .get("/auth/me")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        UserInfoJson userInfoJson = objectMapper.readValue(json, UserInfoJson.class);

        assertThat(userInfoJson.email()).isEqualTo("test@example.com");
        assertThat(userInfoJson.roles()).containsAll(List.of("BOOKMARK_READ", "BOOKMARK_WRITE"));
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
            .cookie("linkweave-credential");

        assertThat(sessionCookie).isNotBlank();

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
}
