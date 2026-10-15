package org.linkweave.infrastructure.db;

import java.time.Duration;

import io.smallrye.mutiny.Uni;
import lombok.experimental.UtilityClass;

/**
 * The {@code SQLITE_BUSY} retry policy for a {@link Uni}.
 *
 * <p>{@code @RetryOnSqliteBusy} cannot help on a reactive path: the interceptor re-invokes a
 * method and inspects what it threw, whereas an asynchronous lookup returns immediately with a
 * {@code Uni} that fails later. The authentication lookup is exactly that shape, and BR-109-3 puts
 * it under the same retry boundary as everything else.</p>
 *
 * <p>What is shared with {@link SqliteBusyRetryInterceptor} is the <em>trigger</em>
 * ({@link SqliteBusy#isBusy}) — the one thing that must never diverge, or the same failure would be
 * retried at one end of a request and not the other. The schedule is deliberately not shared: the
 * interceptor's was tuned against measured p99 batch-write latency and stretches 12 attempts over
 * roughly 1.4&nbsp;s, which is the right trade for a bookmark save and the wrong one here.
 * Identity is resolved in front of <em>every</em> request, so this wait is paid by every request;
 * 4 attempts spend about 70&nbsp;ms in total (delays of roughly 10, 20 and 40&nbsp;ms) and sit at
 * the small end of BR-109-4's budget.</p>
 */
@UtilityClass
public class SqliteBusyRetries {

    /** Total attempts including the initial one, matching {@link RetryOnSqliteBusy#attempts()}. */
    static final int ATTEMPTS = 4;

    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(10);

    /**
     * Ceiling on a single delay. At {@link #ATTEMPTS} it does not bind — doubling from 10&nbsp;ms
     * only reaches 40&nbsp;ms — so it is a bound on the budget rather than part of the schedule:
     * raising the attempt count cannot quietly turn this into a multi-second wait on the path that
     * fronts every request.
     */
    private static final Duration MAX_BACKOFF = Duration.ofMillis(120);

    /**
     * Retries {@code source} while it fails with {@code SQLITE_BUSY}.
     *
     * <p>Mutiny re-subscribes on retry, so each attempt re-runs the supplier that produced the
     * work — a fresh blocking invocation, and therefore a fresh transaction and WAL snapshot. That
     * is the same property the interceptor gets by sitting outside {@code @Transactional}, and it
     * is the only thing that can clear {@code SQLITE_BUSY_SNAPSHOT}: the stale snapshot cannot be
     * waited out, it has to be abandoned.</p>
     *
     * <p>{@code withJitter} spreads collided callers instead of letting them wake together and
     * collide again.</p>
     */
    public static <T> Uni<T> retryWhileBusy(Uni<T> source) {
        return source
            .onFailure(SqliteBusy::isBusy)
            .retry()
            .withBackOff(INITIAL_BACKOFF, MAX_BACKOFF)
            .withJitter(0.5)
            .atMost(ATTEMPTS - 1L);
    }
}
