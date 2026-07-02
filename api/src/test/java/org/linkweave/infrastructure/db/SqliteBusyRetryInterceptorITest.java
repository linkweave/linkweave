package org.linkweave.infrastructure.db;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sqlite.SQLiteErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SqliteBusyRetryInterceptorITest {

    @Inject
    BusyRetryProbe probe;

    @Test
    void shouldRetryUntilSuccessOnSqliteBusy() {
        // ARRANGE
        probe.arm(2, BusyRetryProbe.busyFailure());
        // ACT
        String result = probe.flaky();
        // ASSERT
        assertEquals("ok", result);
        assertEquals(3, probe.calls(), "two busy failures then one success");
    }

    @Test
    void shouldGiveUpAfterConfiguredAttempts() {
        // ARRANGE
        probe.arm(Integer.MAX_VALUE, BusyRetryProbe.busyFailure());
        // ACT
        RuntimeException thrown = assertThrows(RuntimeException.class, probe::flaky);
        // ASSERT
        assertEquals(3, probe.calls(), "attempts=3 means exactly three invocations");
        assertEquals("simulated hibernate wrapper", thrown.getMessage());
    }

    @Test
    void shouldNotRetryOnNonBusyFailures() {
        // ARRANGE
        probe.arm(Integer.MAX_VALUE, new IllegalStateException("unrelated"));
        // ACT
        assertThrows(IllegalStateException.class, probe::flaky);
        // ASSERT
        assertEquals(1, probe.calls(), "non-busy failures must not be retried");
    }

    @ParameterizedTest
    @EnumSource(names = {"SQLITE_BUSY", "SQLITE_BUSY_RECOVERY", "SQLITE_BUSY_SNAPSHOT", "SQLITE_BUSY_TIMEOUT"})
    void shouldRetryAllBusyFlavors(SQLiteErrorCode busyCode) {
        // ARRANGE
        probe.arm(1, BusyRetryProbe.busyFailure(busyCode));
        // ACT
        String result = probe.flaky();
        // ASSERT
        assertEquals("ok", result);
        assertEquals(2, probe.calls(), "one busy failure then one success");
    }

    @Test
    void shouldDetectBusyAttachedAsSuppressedException() {
        // ARRANGE — Narayana attaches commit-time failures via addSuppressed rather than as cause
        probe.arm(1, BusyRetryProbe.suppressedBusyFailure());
        // ACT
        String result = probe.flaky();
        // ASSERT
        assertEquals("ok", result);
        assertEquals(2, probe.calls());
    }

    @Test
    void shouldNotRetryInsideCallerTransaction() {
        // ARRANGE — the caller's transaction is rolled back with the failure, so a retry of only
        // the annotated method could not restore it; the interceptor must pass through untouched
        probe.arm(1, BusyRetryProbe.busyFailure());
        // ACT
        assertThrows(RuntimeException.class,
            () -> QuarkusTransaction.requiringNew().run(probe::flaky));
        // ASSERT
        assertEquals(1, probe.calls(), "no retry when a transaction is already active");
    }

    @Test
    void shouldMatchOnlyBusyPrimaryResultCodes() {
        assertTrue(SqliteBusyRetryInterceptor.isBusyCode(SQLiteErrorCode.SQLITE_BUSY));
        assertTrue(SqliteBusyRetryInterceptor.isBusyCode(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT));
        assertFalse(SqliteBusyRetryInterceptor.isBusyCode(SQLiteErrorCode.SQLITE_LOCKED),
            "SQLITE_LOCKED (6) is a different primary code and must not be retried");
        assertFalse(SqliteBusyRetryInterceptor.isBusyCode(SQLiteErrorCode.SQLITE_CONSTRAINT));
    }
}
