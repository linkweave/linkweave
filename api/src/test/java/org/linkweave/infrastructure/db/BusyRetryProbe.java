package org.linkweave.infrastructure.db;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * Test bean for {@link SqliteBusyRetryInterceptor}: fails a configurable number of times with a
 * (wrapped) {@code SQLITE_BUSY} SQLiteException before succeeding.
 */
@ApplicationScoped
public class BusyRetryProbe {

    private final AtomicInteger calls = new AtomicInteger();
    private volatile int failuresBeforeSuccess;
    private volatile RuntimeException failure;

    public void arm(int failuresBeforeSuccess, RuntimeException failure) {
        this.calls.set(0);
        this.failuresBeforeSuccess = failuresBeforeSuccess;
        this.failure = failure;
    }

    public int calls() {
        return calls.get();
    }

    @RetryOnSqliteBusy(attempts = 3)
    public String flaky() {
        if (calls.incrementAndGet() <= failuresBeforeSuccess) {
            throw failure;
        }
        return "ok";
    }

    public static RuntimeException busyFailure() {
        return busyFailure(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT);
    }

    public static RuntimeException busyFailure(SQLiteErrorCode code) {
        return new RuntimeException("simulated hibernate wrapper",
            new SQLiteException("[" + code.name() + "] database is locked", code));
    }

    /** Busy failure reachable only via {@code getSuppressed()}, as Narayana attaches it on commit. */
    public static RuntimeException suppressedBusyFailure() {
        RuntimeException wrapper = new RuntimeException("simulated commit failure");
        wrapper.addSuppressed(new SQLiteException("[SQLITE_BUSY] database is locked", SQLiteErrorCode.SQLITE_BUSY));
        return wrapper;
    }
}
