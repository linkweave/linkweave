package org.chainlink.api.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class UserSettingsResourceITest {

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldIncludeDefaultSettingsInUserInfo() {
        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(200)
            .body("settings.offlineCachingEnabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldUpdateOfflineCachingAndReflectInUserInfo() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"offlineCachingEnabled\":false}")
            .put("/auth/settings")
            .then()
            .statusCode(200)
            .body("offlineCachingEnabled", equalTo(false));

        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(200)
            .body("settings.offlineCachingEnabled", equalTo(false));

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"offlineCachingEnabled\":true}")
            .put("/auth/settings")
            .then()
            .statusCode(200)
            .body("offlineCachingEnabled", equalTo(true));
    }
}
