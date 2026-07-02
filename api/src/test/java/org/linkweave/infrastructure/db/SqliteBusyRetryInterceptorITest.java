package org.linkweave.infrastructure.db;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
