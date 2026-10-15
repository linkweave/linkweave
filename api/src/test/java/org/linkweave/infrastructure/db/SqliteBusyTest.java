package org.linkweave.infrastructure.db;

import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared contention predicate. Two decisions rest on it — retry or not, 503 or 500 — so it is
 * tested directly rather than through either of them.
 */
class SqliteBusyTest {

    @Test
    void shouldRecogniseContentionInEveryShapeItArrivesIn() {
        assertThat(SqliteBusy.isBusy(SqliteBusyFailures.bare())).isTrue();
        assertThat(SqliteBusy.isBusy(SqliteBusyFailures.onRead())).isTrue();
        assertThat(SqliteBusy.isBusy(SqliteBusyFailures.onFlush())).isTrue();
        assertThat(SqliteBusy.isBusy(SqliteBusyFailures.onCommit())).isTrue();
        assertThat(SqliteBusy.isBusy(SqliteBusyFailures.suppressedByFailedRollback())).isTrue();
    }

    @Test
    void shouldNotRecogniseOtherFailures() {
        assertThat(SqliteBusy.isBusy(null)).isFalse();
        assertThat(SqliteBusy.isBusy(new IllegalStateException("unrelated"))).isFalse();
        assertThat(SqliteBusy.isBusy(SqliteBusyFailures.bare(SQLiteErrorCode.SQLITE_LOCKED)))
            .isFalse();
    }

    @Test
    void shouldTerminateWhenTheExceptionGraphIsCyclic() {
        // Not a contrived input: Mutiny marks an exhausted retry by suppressing an exception whose
        // cause is the failure that suppresses it (see the regression test in SqliteBusyRetriesTest
        // for the real thing). Following causes and suppressed exceptions therefore walks a graph
        // with cycles, and a recursive walk over this shape overflowed the stack at the request
        // boundary — turning the 503 back into the 500 this predicate exists to prevent.
        RuntimeException outer = new RuntimeException("outer", SqliteBusyFailures.bare());
        outer.addSuppressed(new IllegalStateException("Retries exhausted: 3/3", outer));

        assertThat(SqliteBusy.isBusy(outer)).isTrue();
    }

    @Test
    void shouldTerminateOnACyclicGraphThatHoldsNoContention() {
        // The same cycle, but nothing in it is BUSY — so termination cannot come from returning
        // early on a match. This is the case that actually requires the visited set.
        RuntimeException outer = new RuntimeException("outer");
        RuntimeException inner = new RuntimeException("inner", outer);
        outer.addSuppressed(inner);
        inner.addSuppressed(outer);

        assertThat(SqliteBusy.isBusy(outer)).isFalse();
    }

    @Test
    void shouldMatchOnlyBusyPrimaryResultCodes() {
        assertThat(SqliteBusy.isBusyCode(SQLiteErrorCode.SQLITE_BUSY)).isTrue();
        assertThat(SqliteBusy.isBusyCode(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT)).isTrue();
        assertThat(SqliteBusy.isBusyCode(SQLiteErrorCode.SQLITE_LOCKED))
            .as("SQLITE_LOCKED (6) is a different primary code and must not be retried")
            .isFalse();
        assertThat(SqliteBusy.isBusyCode(SQLiteErrorCode.SQLITE_CONSTRAINT)).isFalse();
    }
}
