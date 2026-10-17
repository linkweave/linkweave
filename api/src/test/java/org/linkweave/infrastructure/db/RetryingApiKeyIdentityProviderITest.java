package org.linkweave.infrastructure.db;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the API-key identity lookup is decorated (BR-109-3).
 *
 * <p>Same reasoning as {@code RetryingTrustedIdentityProviderITest}: if ArC stops applying the
 * decorator, nothing breaks and nothing fails — API-key requests authenticate exactly as before
 * and the retry is simply gone. The sustained-contention case in
 * {@code SqliteBusyContentionInjectionITest} cannot see that either, because the 503 it asserts
 * comes from the request boundary whether or not a retry was attempted first. So the wiring is
 * asserted directly.</p>
 */
@QuarkusTest
class RetryingApiKeyIdentityProviderITest {

    @Test
    void shouldRouteApiKeyAuthenticationThroughTheRetryDecorator() {
        // ARRANGE — a real key, issued through the real endpoint to a form-authenticated session
        String sessionCookie = RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "test@example.com")
            .formParam("j_password", "test")
            .post("/j_security_check")
            .then().statusCode(200)
            .extract().cookie("linkweave-credential");
        String apiKey = RestAssured.given()
            .cookie("linkweave-credential", sessionCookie)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"DecoratorProbeKey\"}")
            .post("/auth/api-keys")
            .then().statusCode(201)
            .extract().path("key");
        long before = RetryingApiKeyIdentityProvider.DELEGATE_SUBSCRIPTIONS.get();

        // ACT
        RestAssured.given()
            .header("X-API-Key", apiKey)
            .get("/collections")
            .then().statusCode(200);

        // ASSERT
        assertThat(RetryingApiKeyIdentityProvider.DELEGATE_SUBSCRIPTIONS.get())
            .as("API-key authentication must pass through the retry decorator; if this does not "
                + "move, BR-109-3 is silently unmet for CLI and extension traffic")
            .isGreaterThan(before);
    }
}
