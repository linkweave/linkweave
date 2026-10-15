package org.linkweave.infrastructure.db;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test bean for {@link SqliteBusyRetryInterceptor}: fails a configurable number of times before
 * succeeding. The failure to throw comes from {@link SqliteBusyFailures}, so the interceptor is
 * exercised with the same inputs as the rest of the contention handling.
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
}
