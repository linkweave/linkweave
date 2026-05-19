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
    void shouldGetDefaultSettings() {
        RestAssured.given()
            .get("/auth/settings")
            .then()
            .statusCode(200)
            .body("offlineCachingEnabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldUpdateOfflineCachingEnabled() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"offlineCachingEnabled\":false}")
            .put("/auth/settings")
            .then()
            .statusCode(200)
            .body("offlineCachingEnabled", equalTo(false));

        RestAssured.given()
            .get("/auth/settings")
            .then()
            .statusCode(200)
            .body("offlineCachingEnabled", equalTo(false));

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"offlineCachingEnabled\":true}")
            .put("/auth/settings")
            .then()
            .statusCode(200)
            .body("offlineCachingEnabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldIncludeSettingsInUserInfo() {
        RestAssured.given()
            .get("/auth/me")
            .then()
            .statusCode(200)
            .body("settings.offlineCachingEnabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReflectUpdatedSettingsInUserInfo() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"offlineCachingEnabled\":false}")
            .put("/auth/settings")
            .then()
            .statusCode(200);

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
            .statusCode(200);
    }
}
