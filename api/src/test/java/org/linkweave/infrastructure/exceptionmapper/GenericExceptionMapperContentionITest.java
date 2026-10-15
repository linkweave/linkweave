package org.linkweave.infrastructure.exceptionmapper;

import org.linkweave.infrastructure.db.SqliteBusyFailures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-109-1: lock contention must never reach a user as an unhandled 500.
 *
 * <p>Inputs come from {@link SqliteBusyFailures} rather than being bare {@code SQLiteException}s,
 * because the whole difficulty of this rule is that contention arrives wrapped in whatever the
 * failing layer throws — which is why it is classified in the catch-all mapper instead of a typed
 * {@code @Provider}.</p>
 */
@QuarkusTest
class GenericExceptionMapperContentionITest {

    @Inject
    GenericExceptionMapper mapper;

    @Test
    void shouldAnswer503WithRetryAfterWhenAReadHitsContention() {
        // ARRANGE — the shape the authentication lookup failed with
        var failure = SqliteBusyFailures.onRead();

        // ACT
        Response response = mapper.toResponse(failure);

        // ASSERT
        assertThat(response.getStatus())
            .as("contention is transient, so it must be retryable (503), never a crash (500)")
            .isEqualTo(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
        assertThat(response.getHeaderString(HttpHeaders.RETRY_AFTER))
            .as("the client needs to know it may retry, and roughly when")
            .isNotBlank();
    }

    @Test
    void shouldAnswer503WhenContentionSurfacesOnlyOnCommit() {
        assertThat(mapper.toResponse(SqliteBusyFailures.onCommit()).getStatus())
            .isEqualTo(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
    }

    @Test
    void shouldAnswer503WhenContentionIsOnlyInASuppressedException() {
        assertThat(mapper.toResponse(SqliteBusyFailures.suppressedByFailedRollback()).getStatus())
            .isEqualTo(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
    }

    @Test
    void shouldStillAnswer500ForFailuresThatAreNotContention() {
        // The guard must not turn every unexpected error into a retryable one: telling a client to
        // retry a genuine bug just produces the same failure again, more slowly.
        assertThat(mapper.toResponse(new IllegalStateException("a real bug")).getStatus())
            .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }
}
