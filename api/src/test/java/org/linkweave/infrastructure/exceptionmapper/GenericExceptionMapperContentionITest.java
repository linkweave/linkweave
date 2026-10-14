package org.linkweave.infrastructure.exceptionmapper;

import java.sql.SQLException;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PessimisticLockException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-109-1: lock contention must never reach a user as an unhandled 500.
 *
 * <p>Inputs are shaped like the real thing rather than a bare {@link SQLiteException}, because the
 * whole difficulty of this rule is that contention arrives wrapped in whatever the failing layer
 * throws — which is why it is classified in the catch-all mapper instead of a typed
 * {@code @Provider}. The 2026-08-28 incident produced all three of these shapes.</p>
 */
@QuarkusTest
class GenericExceptionMapperContentionITest {

    @Inject
    GenericExceptionMapper mapper;

    private static SQLiteException busy() {
        return new SQLiteException("database is locked", SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT);
    }

    @Test
    void shouldAnswer503WithRetryAfterWhenAReadHitsContention() {
        // ARRANGE — the shape the authentication lookup failed with: a read, inside JPA
        var failure = new PessimisticLockException("could not obtain lock",
            new SQLException("JDBC exception executing SQL", busy()));

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
        // The Arjuna onePhaseCommit shape: nothing above the commit knows it was contention, and
        // the SQLiteException is two levels down.
        var failure = new IllegalStateException(
            "ARJUNA016039: onePhaseCommit failed",
            new RuntimeException("Error trying to transactionCommit local transaction", busy()));

        assertThat(mapper.toResponse(failure).getStatus())
            .isEqualTo(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
    }

    @Test
    void shouldAnswer503WhenContentionIsOnlyInASuppressedException() {
        // A failed rollback attaches the original failure as suppressed rather than as a cause.
        var failure = new IllegalStateException("rollback failed");
        failure.addSuppressed(busy());

        assertThat(mapper.toResponse(failure).getStatus())
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
