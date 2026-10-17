package org.linkweave.infrastructure.db;

import org.linkweave.api.auth.apikey.ApiKeyRequest;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @Inject
    IdentityProviderManager identityProviderManager;

    @Inject
    EntityManagerFactory entityManagerFactory;

    private HibernateStatisticsProbe statistics;

    @BeforeEach
    void enableStatistics() {
        statistics = new HibernateStatisticsProbe(entityManagerFactory);
        statistics.enable();
    }

    @AfterEach
    void restoreStatistics() {
        statistics.restore();
    }

    @Test
    void shouldRouteApiKeyAuthenticationThroughTheRetryDecorator() {
        // ARRANGE
        String apiKey = issueApiKey("DecoratorProbeKey");
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

    /**
     * The retry is only worth anything if re-subscribing actually re-runs the lookup.
     *
     * <p>The mirror of {@code RetryingTrustedIdentityProviderITest}'s version, and needed for the
     * same reason even though this delegate is our own code: {@code ApiKeyIdentityProvider} returns
     * {@code context.runBlocking(…)}, so whether the {@link Uni} is lazy is decided by
     * {@code AuthenticationRequestContext}, not by us. If a Quarkus upgrade made it eager or
     * memoised, the retry would faithfully re-subscribe and faithfully replay the same cached
     * failure — and the wiring test above would stay green while the retry did nothing.
     *
     * <p>A fresh {@code SecurityIdentity} is not sufficient evidence on its own, since an augmentor
     * produces one too, so the statement counter carries the claim.
     */
    @Test
    void shouldReRunTheLookupOnEverySubscription() {
        // ARRANGE
        String apiKey = issueApiKey("ResubscriptionProbeKey");
        long before = RetryingApiKeyIdentityProvider.DELEGATE_SUBSCRIPTIONS.get();
        Uni<SecurityIdentity> lookup =
            identityProviderManager.authenticate(new ApiKeyRequest(apiKey));
        long statementsBefore = statistics.statements();

        // ACT — one Uni, two subscriptions, exactly as a retry does it
        SecurityIdentity first = lookup.await().indefinitely();
        long statementsAfterFirst = statistics.statements();
        SecurityIdentity second = lookup.await().indefinitely();

        // ASSERT
        assertThat(first.getPrincipal().getName()).isEqualTo("test@example.com");
        assertThat(second).isNotSameAs(first);
        assertThat(statistics.statements() - statementsBefore)
            .as("the second subscription must have gone back to the database; a new "
                + "SecurityIdentity instance alone would not prove that")
            .isGreaterThan(statementsAfterFirst - statementsBefore);
        assertThat(RetryingApiKeyIdentityProvider.DELEGATE_SUBSCRIPTIONS.get() - before)
            .as("each subscription must reach the delegate through the decorator")
            .isEqualTo(2);
    }

    /** A real key, issued through the real endpoint to a form-authenticated session. */
    private String issueApiKey(String name) {
        String sessionCookie = RestAssured.given()
            .contentType(ContentType.URLENC)
            .formParam("j_username", "test@example.com")
            .formParam("j_password", "test")
            .post("/j_security_check")
            .then().statusCode(200)
            .extract().cookie("linkweave-credential");
        return RestAssured.given()
            .cookie("linkweave-credential", sessionCookie)
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + name + "\"}")
            .post("/auth/api-keys")
            .then().statusCode(201)
            .extract().path("key");
    }
}
