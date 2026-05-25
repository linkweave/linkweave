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
            .body("settings.offlineCachingEnabled", equalTo(true))
            .body("settings.savedSearchesEnabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldUpdateOfflineCachingAndReflectInUserInfo() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"offlineCachingEnabled\":false,\"savedSearchesEnabled\":true}")
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
            .body("{\"offlineCachingEnabled\":true,\"savedSearchesEnabled\":true}")
            .put("/auth/settings")
            .then()
            .statusCode(200)
            .body("offlineCachingEnabled", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldToggleSavedSearchesEnabled() {
        try {
            RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"offlineCachingEnabled\":true,\"savedSearchesEnabled\":false}")
                .put("/auth/settings")
                .then()
                .statusCode(200)
                .body("savedSearchesEnabled", equalTo(false));

            RestAssured.given()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("settings.savedSearchesEnabled", equalTo(false));
        } finally {
            // Restore default so test order does not leak state into the other tests.
            RestAssured.given()
                .contentType(ContentType.JSON)
                .body("{\"offlineCachingEnabled\":true,\"savedSearchesEnabled\":true}")
                .put("/auth/settings");
        }
    }
}
