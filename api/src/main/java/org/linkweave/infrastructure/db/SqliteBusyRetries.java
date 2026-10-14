package org.linkweave.infrastructure.db;

import java.time.Duration;

import io.smallrye.mutiny.Uni;
import lombok.experimental.UtilityClass;

/**
 * The retry policy of {@link SqliteBusyRetryInterceptor}, expressed for a {@link Uni}.
 *
 * <p>{@code @RetryOnSqliteBusy} cannot help on a reactive path: the interceptor re-invokes a
 * method and inspects what it threw, whereas an asynchronous lookup returns immediately with a
 * {@code Uni} that fails later. The authentication lookup is exactly that shape, and BR-109-3 puts
 * it under the same retry boundary as everything else, so the policy is restated here rather than
 * reinvented — same trigger ({@link SqliteBusy#isBusy}), same shape (capped exponential backoff
 * with jitter), same intent.</p>
 *
 * <p>Deliberately fewer attempts than a bookmark save: identity is resolved in front of every
 * request, so its wait is paid by every request. BR-109-4 caps the whole product at a few seconds,
 * and this path must stay at the small end of that budget.</p>
 */
@UtilityClass
public class SqliteBusyRetries {

    static final int ATTEMPTS = 4;
    static final Duration INITIAL_BACKOFF = Duration.ofMillis(10);
    static final Duration MAX_BACKOFF = Duration.ofMillis(120);

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
