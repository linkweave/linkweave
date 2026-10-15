package org.linkweave.infrastructure.db;

import java.util.concurrent.atomic.AtomicInteger;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The reactive half of the retry boundary (BR-109-3). The interceptor's own behaviour is covered by
 * {@code SqliteBusyRetryInterceptorITest}; this is the policy that applies where there is no method
 * to intercept, only a {@link Uni} that fails later. Inputs come from {@link SqliteBusyFailures}.
 */
class SqliteBusyRetriesTest {

    @Test
    void shouldRetryUntilTheBusyConditionClears() {
        // ARRANGE — fails twice, then succeeds, like a lock holder that commits
        AtomicInteger attempts = new AtomicInteger();
        Uni<String> flaky = Uni.createFrom().item(() -> {
            if (attempts.incrementAndGet() <= 2) {
                throw SqliteBusyFailures.onRead();
            }
            return "resolved";
        });

        // ACT
        String result = SqliteBusyRetries.retryWhileBusy(flaky).await().indefinitely();

        // ASSERT
        assertThat(result).isEqualTo("resolved");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void shouldResubscribeSoEachAttemptGetsAFreshSnapshot() {
        // A stale WAL snapshot cannot be waited out, so a retry is only useful if it re-runs the
        // work rather than re-reading a cached failure. Counting subscriptions proves it does.
        AtomicInteger subscriptions = new AtomicInteger();
        Uni<String> alwaysBusy = Uni.createFrom().<String>emitter(e -> {
            subscriptions.incrementAndGet();
            e.fail(SqliteBusyFailures.onRead());
        });

        assertThatThrownBy(() -> SqliteBusyRetries.retryWhileBusy(alwaysBusy).await().indefinitely())
            .hasRootCauseInstanceOf(SQLiteException.class);

        assertThat(subscriptions)
            .as("every attempt must re-subscribe, not replay a memoised failure")
            .hasValue(SqliteBusyRetries.ATTEMPTS);
    }

    @Test
    void shouldGiveUpAfterTheConfiguredAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        Uni<String> alwaysBusy = Uni.createFrom().item(() -> {
            attempts.incrementAndGet();
            throw SqliteBusyFailures.onCommit();
        });

        assertThatThrownBy(() -> SqliteBusyRetries.retryWhileBusy(alwaysBusy).await().indefinitely())
            .hasRootCauseInstanceOf(SQLiteException.class);

        assertThat(attempts)
            .as("the wait a request can incur must stay bounded (BR-109-4)")
            .hasValue(SqliteBusyRetries.ATTEMPTS);
    }

    @Test
    void shouldNotRetryFailuresThatAreNotContention() {
        // A constraint violation will fail identically on every attempt; retrying only spends the
        // caller's latency budget.
        AtomicInteger attempts = new AtomicInteger();
        Uni<String> broken = Uni.createFrom().item(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("not contention");
        });

        // Asserted on the message rather than the type: Mutiny surfaces a bare failure directly
        // and a wrapped one through a cause chain, and which of those happens is not the point.
        assertThatThrownBy(() -> SqliteBusyRetries.retryWhileBusy(broken).await().indefinitely())
            .hasMessageContaining("not contention");

        assertThat(attempts).hasValue(1);
    }

    @Test
    void shouldLeaveAnExhaustedFailureStillRecognisableAsContention() {
        // The request boundary classifies the very failure this method hands back, and Mutiny
        // marks exhaustion by suppressing an IllegalStateException whose cause is that failure —
        // so the graph it returns contains a cycle. Walking it recursively overflowed the stack
        // inside SqliteBusy, and the caller got a 500 instead of the 503 the retry was supposed to
        // lead to. Measured end to end in SqliteBusyContentionInjectionITest; pinned here.
        Uni<String> alwaysBusy = Uni.createFrom().item(() -> {
            throw SqliteBusyFailures.onRead();
        });

        Throwable exhausted = catchThrowable(
            () -> SqliteBusyRetries.retryWhileBusy(alwaysBusy).await().indefinitely());

        assertThat(SqliteBusy.isBusy(exhausted))
            .as("an exhausted retry must still be classified as contention, not as a crash")
            .isTrue();
    }
}
