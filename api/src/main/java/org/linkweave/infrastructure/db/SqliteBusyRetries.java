package org.linkweave.infrastructure.db;

import java.time.Duration;

import io.smallrye.mutiny.Uni;
import lombok.experimental.UtilityClass;

/**
 * The {@code SQLITE_BUSY} retry policy for a {@link Uni}.
 *
 * <p>{@code @RetryOnSqliteBusy} cannot help on a reactive path: the interceptor re-invokes a
 * method and inspects what it threw, whereas an asynchronous lookup returns immediately with a
 * {@code Uni} that fails later. Both identity lookups are exactly that shape, and BR-109-3 puts
 * them under the same retry boundary as everything else.</p>
 *
 * <p>What is shared with {@link SqliteBusyRetryInterceptor} is the <em>trigger</em>
 * ({@link SqliteBusy#isBusy}) — the one thing that must never diverge, or the same failure would be
 * retried at one end of a request and not the other. The schedule is deliberately not shared: the
 * interceptor's was tuned against measured p99 batch-write latency and stretches 12 attempts over
 * roughly 1.4&nbsp;s, which is the right trade for a bookmark save and the wrong one in front of
 * every request.</p>
 *
 * <h2>Why there is a wall-clock budget and not just an attempt count</h2>
 *
 * <p>The backoff schedule is small — delays of roughly 10, 20 and 40&nbsp;ms, about 70&nbsp;ms in
 * total — but that is only the time <em>this</em> policy adds. Each attempt can also block inside
 * the driver: the datasource sets {@code busy_timeout=10000}, so an attempt that waits on a lock
 * (the checkpoint and WAL-recovery windows that make a read fail in production) can burn up to ten
 * seconds before the retry policy is told anything at all. On attempt count alone, four attempts
 * would therefore be up to <b>forty seconds</b> on the path in front of every request, each attempt
 * holding an Agroal connection and a worker thread — pool exhaustion in precisely the scenario this
 * is meant to defend. Before this change the same contention cost one such wait and then a 500, so
 * counting attempts without bounding time would have made the worst case four times worse.</p>
 *
 * <p>So retrying stops as soon as {@link #BUDGET} is spent, whichever comes first. Contention that
 * fails fast — {@code SQLITE_BUSY_SNAPSHOT} ignores {@code busy_timeout} entirely — still gets all
 * {@link #ATTEMPTS} attempts, because the whole schedule fits inside the budget. Contention that
 * blocks in the driver gets one attempt and then an immediate answer, which is no worse than the
 * behaviour this replaced.</p>
 *
 * <p>This bounds the composition; it does not fix it. The real fix is BR-109-4: reduce the driver's
 * {@code busy_timeout} so the application does the waiting and the product of the two is one
 * explicit budget. That is deliberately not in this change, and NFR-030 stays open until it is.</p>
 */
@UtilityClass
public class SqliteBusyRetries {

    /** Total attempts including the initial one, matching {@link RetryOnSqliteBusy#attempts()}. */
    static final int ATTEMPTS = 4;

    /**
     * Ceiling on the total wall time retrying may add, counted from the first subscription.
     *
     * <p>Half of NFR-002's 500&nbsp;ms request budget: this runs before a request does any of its
     * own work, so it cannot have all of it. It is above the full backoff schedule (~70&nbsp;ms)
     * on purpose — the budget is there to stop a <em>driver</em> wait from being multiplied, not to
     * cut the schedule short.</p>
     */
    static final Duration BUDGET = Duration.ofMillis(250);

    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(10);

    /**
     * Ceiling on a single delay. At {@link #ATTEMPTS} it does not bind — doubling from 10&nbsp;ms
     * only reaches 40&nbsp;ms — so it guards against a future increase in the attempt count rather
     * than shaping today's schedule.
     */
    private static final Duration MAX_BACKOFF = Duration.ofMillis(120);

    /**
     * Retries {@code source} while it fails with {@code SQLITE_BUSY} and the budget holds.
     *
     * <p>Mutiny re-subscribes on retry, so each attempt re-runs the supplier that produced the
     * work — a fresh blocking invocation, and therefore a fresh transaction and WAL snapshot. That
     * is the same property the interceptor gets by sitting outside {@code @Transactional}, and it
     * is the only thing that can clear {@code SQLITE_BUSY_SNAPSHOT}: the stale snapshot cannot be
     * waited out, it has to be abandoned.</p>
     *
     * <p>The deadline lives in {@code deferred} so that it is per-subscription rather than shared
     * by every caller of the returned {@code Uni}, and it is folded into the
     * {@code onFailure} predicate: when the budget is gone the failure is simply not selected for
     * retry, so it propagates immediately and the request boundary answers 503 (BR-109-1).</p>
     *
     * <p>{@code withJitter} spreads collided callers instead of letting them wake together and
     * collide again.</p>
     */
    public static <T> Uni<T> retryWhileBusy(Uni<T> source) {
        return Uni.createFrom().deferred(() -> {
            long deadline = System.nanoTime() + BUDGET.toNanos();
            return source
                .onFailure(failure -> SqliteBusy.isBusy(failure) && System.nanoTime() < deadline)
                .retry()
                .withBackOff(INITIAL_BACKOFF, MAX_BACKOFF)
                .withJitter(0.5)
                .atMost(ATTEMPTS - 1L);
        });
    }
}
