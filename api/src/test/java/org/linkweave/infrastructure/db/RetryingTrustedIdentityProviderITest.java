package org.linkweave.infrastructure.db;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the identity lookup is actually decorated (BR-109-3).
 *
 * <p>The retry policy itself is tested in {@code SqliteBusyRetriesTest}. What cannot be tested
 * there is the part that is not our code: whether ArC applies a CDI decorator to the
 * {@code IdentityProvider} that {@code quarkus-security-jpa} generates from
 * {@code @UserDefinition}. If it ever stops doing so, nothing breaks and no test fails — requests
 * authenticate exactly as before, and the retry boundary is quietly gone. So this asserts the
 * wiring rather than the behaviour.</p>
 */
@QuarkusTest
class RetryingTrustedIdentityProviderITest {

    @Test
    void shouldRouteTheTrustedIdentityLookupThroughTheRetryDecorator() {
        // ARRANGE
        long before = RetryingTrustedIdentityProvider.DECORATED_LOOKUPS.get();
        String sessionCookie = RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "test@example.com")
            .formParam("j_password", "test")
            .post("/j_security_check")
            .then()
            .statusCode(200)
            .extract()
            .cookie("linkweave-credential");

        // ACT — an authenticated request resolves identity from the session cookie, which is the
        // TrustedAuthenticationRequest path this decorator wraps.
        RestAssured.given()
            .cookie("linkweave-credential", sessionCookie)
            .get("/auth/me")
            .then()
            .statusCode(200);

        // ASSERT
        assertThat(RetryingTrustedIdentityProvider.DECORATED_LOOKUPS.get())
            .as("the generated trusted identity provider must be wrapped by the retry decorator; "
                + "if this is 0 the decorator is no longer applied and BR-109-3 is silently unmet")
            .isGreaterThan(before);
    }
}
