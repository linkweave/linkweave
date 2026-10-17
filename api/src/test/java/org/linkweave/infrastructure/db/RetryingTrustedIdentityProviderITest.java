package org.linkweave.infrastructure.db;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @Inject
    IdentityProviderManager identityProviderManager;

    @Inject
    EntityManagerFactory entityManagerFactory;

    private boolean statisticsWereEnabled;

    /**
     * On for this class only. It used to be on for the whole {@code %test} profile, which made
     * every other integration test pay per-statement bookkeeping for one assertion here.
     */
    @BeforeEach
    void enableStatistics() {
        statisticsWereEnabled = statistics().isStatisticsEnabled();
        statistics().setStatisticsEnabled(true);
    }

    @AfterEach
    void restoreStatistics() {
        statistics().setStatisticsEnabled(statisticsWereEnabled);
    }

    @Test
    void shouldRouteTheTrustedIdentityLookupThroughTheRetryDecorator() {
        // ARRANGE
        long before = RetryingTrustedIdentityProvider.DELEGATE_SUBSCRIPTIONS.get();
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
        assertThat(RetryingTrustedIdentityProvider.DELEGATE_SUBSCRIPTIONS.get())
            .as("the generated trusted identity provider must be wrapped by the retry decorator; "
                + "if this is 0 the decorator is no longer applied and BR-109-3 is silently unmet")
            .isGreaterThan(before);
    }

    /**
     * The retry is only worth anything if re-subscribing actually re-runs the lookup.
     *
     * <p>{@code SqliteBusyRetriesTest} proves Mutiny re-subscribes, but it proves it against a
     * {@link Uni} the test itself built. The {@link Uni} that matters is the one the
     * {@code quarkus-security-jpa} provider returns, and nothing in our code decides whether that
     * one is lazy. If a Quarkus upgrade ever made it eager or memoised, the retry would faithfully
     * re-subscribe and faithfully replay the same cached failure — brief contention would stop
     * being absorbed, and the only test that would notice today is behind a gate.
     *
     * <p>So this subscribes twice to one {@code Uni} and checks the work happened twice. A fresh
     * {@code SecurityIdentity} instance is the evidence: the provider constructs one inside the
     * blocking supplier, so a second instance can only exist if that supplier ran again — which is
     * where the database query lives. A memoised {@code Uni} would hand back the first instance.
     */
    @Test
    void shouldReRunTheLookupOnEverySubscription() {
        // ARRANGE
        long before = RetryingTrustedIdentityProvider.DELEGATE_SUBSCRIPTIONS.get();
        Uni<SecurityIdentity> lookup = identityProviderManager.authenticate(
            new TrustedAuthenticationRequest("test@example.com"));
        long statementsBefore = statements();

        // ACT — one Uni, two subscriptions, exactly as a retry does it
        SecurityIdentity first = lookup.await().indefinitely();
        long statementsAfterFirst = statements();
        SecurityIdentity second = lookup.await().indefinitely();

        // ASSERT
        assertThat(first.getPrincipal().getName()).isEqualTo("test@example.com");
        assertThat(second).isNotSameAs(first);
        assertThat(statements() - statementsBefore)
            .as("the second subscription must have gone back to the database; a new "
                + "SecurityIdentity instance alone would not prove that, since an augmentor "
                + "produces one too")
            .isGreaterThan(statementsAfterFirst - statementsBefore);
        assertThat(RetryingTrustedIdentityProvider.DELEGATE_SUBSCRIPTIONS.get() - before)
            .as("each subscription must reach the delegate through the decorator")
            .isEqualTo(2);
    }

    /** Asserted enabled rather than assumed: off, the counter stays at zero and nothing is proven. */
    private long statements() {
        assertThat(statistics().isStatisticsEnabled()).isTrue();
        return statistics().getPrepareStatementCount();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
