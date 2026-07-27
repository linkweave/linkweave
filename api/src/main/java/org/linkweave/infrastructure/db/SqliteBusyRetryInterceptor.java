package org.linkweave.infrastructure.db;

import java.util.concurrent.ThreadLocalRandom;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * Retries the intercepted invocation when it failed with {@code SQLITE_BUSY} (see
 * {@link RetryOnSqliteBusy} for when that happens and what is safe to annotate).
 *
 * <p><b>Ordering:</b> priority 150 places this interceptor <em>outside</em> Narayana's
 * {@code @Transactional} interceptor (priority {@code PLATFORM_BEFORE + 200}), so each retry runs
 * in a fresh transaction with a fresh WAL snapshot. As a guard, invocations that are already
 * inside a caller's transaction are never retried — the rollback discarded that caller's work,
 * and re-running only the inner method would not restore it.</p>
 */
@Interceptor
@RetryOnSqliteBusy
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 150)
@RequiredArgsConstructor
@Slf4j
public class SqliteBusyRetryInterceptor {

    private static final long BACKOFF_BASE_MS = 10;
    private static final long BACKOFF_CAP_MS = 250;

    private final TransactionManager transactionManager;

    @AroundInvoke
    Object retryOnBusy(InvocationContext ctx) throws Exception {
        if (transactionManager.getStatus() != Status.STATUS_NO_TRANSACTION) {
            LOG.warn("@RetryOnSqliteBusy on {}#{} is ineffective: caller already has an active transaction, "
                    + "so a SQLITE_BUSY failure cannot be retried here — move the annotation to the "
                    + "outermost transaction boundary",
                ctx.getMethod().getDeclaringClass().getSimpleName(), ctx.getMethod().getName());
            return ctx.proceed();
        }
        int attempts = Math.max(1, resolveAttempts(ctx));
        for (int attempt = 1; ; attempt++) {
            try {
                return ctx.proceed();
            } catch (Exception e) {
                if (attempt >= attempts || !isSqliteBusy(e)) {
                    throw e;
                }
                LOG.debug("SQLITE_BUSY on {}#{}, retrying (attempt {} of {})",
                    ctx.getMethod().getDeclaringClass().getSimpleName(), ctx.getMethod().getName(),
                    attempt, attempts);
                sleepWithJitter(attempt, e);
            }
        }
    }

    private int resolveAttempts(InvocationContext ctx) {
        RetryOnSqliteBusy onMethod = ctx.getMethod().getAnnotation(RetryOnSqliteBusy.class);
        if (onMethod != null) {
            return onMethod.attempts();
        }
        RetryOnSqliteBusy onClass = ctx.getMethod().getDeclaringClass().getAnnotation(RetryOnSqliteBusy.class);
        return onClass != null ? onClass.attempts() : 1;
    }

    /**
     * Capped exponential backoff with equal jitter.
     *
     * <p>The previous schedule was linear ({@code 10ms * attempt}), which gave the 12-attempt batch
     * endpoints a total retry window of roughly 660&nbsp;ms. Measured batch write latency under
     * concurrency has a p99 near 900&nbsp;ms, so the retries could expire while the lock holder was
     * still committing — the caller then got a 500 even though simply waiting longer would have
     * succeeded. Doubling with a 250&nbsp;ms ceiling stretches the same 12 attempts to roughly
     * 1.4&nbsp;s while keeping early retries fast, so brief contention still clears immediately.</p>
     *
     * <p>Half the delay is fixed and half random ("equal jitter"): writers that collided once are
     * spread out instead of waking together and colliding again.</p>
     */
    private void sleepWithJitter(int attempt, Exception cause) throws Exception {
        long ceiling = Math.min(BACKOFF_CAP_MS, BACKOFF_BASE_MS << Math.min(attempt - 1, 20));
        long delay = ceiling / 2 + ThreadLocalRandom.current().nextLong(ceiling / 2 + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    static boolean isSqliteBusy(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLiteException sqlite && isBusyCode(sqlite.getResultCode())) {
                return true;
            }
            for (Throwable suppressed : t.getSuppressed()) {
                if (isSqliteBusy(suppressed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Matches any BUSY flavor. Per the SQLite result-code convention
     * (https://sqlite.org/rescode.html), the least significant 8 bits of an extended result code
     * are its primary result code — e.g. {@code SQLITE_BUSY_SNAPSHOT} = 517 = {@code (2 << 8) | 5}
     * extends {@code SQLITE_BUSY} = 5. Masking keeps this future-proof for busy variants this
     * driver version does not know yet.
     */
    static boolean isBusyCode(SQLiteErrorCode resultCode) {
        return (resultCode.code & 0xff) == SQLiteErrorCode.SQLITE_BUSY.code;
    }
}
