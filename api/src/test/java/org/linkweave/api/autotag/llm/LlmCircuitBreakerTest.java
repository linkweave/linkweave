package org.linkweave.api.autotag.llm;

import java.time.Duration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.linkweave.api.shared.config.ConfigService;

/**
 * Unit tests for {@link LlmCircuitBreaker} (UC-108 BR-108-4, BR-108-9).
 *
 * <p>Cooldowns are configured in milliseconds here so the half-open probe can be
 * observed without the test sleeping for the production 30 seconds.
 */
class LlmCircuitBreakerTest {

    @Test
    void shouldOpenAfterThresholdAndRefuseFurtherCallsWithoutContactingTheModel() {
        // ARRANGE
        LlmCircuitBreaker breaker = newBreaker(3, Duration.ofSeconds(30), 4);

        // ACT — two failures are not yet enough; the third opens the circuit.
        failOnce(breaker, LlmFailure.TIMEOUT);
        failOnce(breaker, LlmFailure.TIMEOUT);
        Assertions.assertThat(breaker.tryAcquire().admitted())
            .as("below the threshold the model is still tried")
            .isTrue();
        failOnce(breaker, LlmFailure.TIMEOUT);

        // ASSERT
        LlmCircuitBreaker.Admission refused = breaker.tryAcquire();
        Assertions.assertThat(refused.admitted())
            .as("once open, no further call reaches the model")
            .isFalse();
        Assertions.assertThat(refused.outcome()).isEqualTo(SuggestionOutcome.CIRCUIT_OPEN);
        Assertions.assertThat(breaker.isOpen()).isTrue();
    }

    @Test
    void shouldOpenImmediatelyWhenTheServiceIsUnreachable() {
        // ARRANGE — a threshold high enough that counting to it would not open.
        LlmCircuitBreaker breaker = newBreaker(5, Duration.ofSeconds(30), 4);

        // ACT — connection refused: nothing is listening (A3).
        failOnce(breaker, LlmFailure.UNREACHABLE);

        // ASSERT
        Assertions.assertThat(breaker.isOpen())
            .as("a refused socket needs no retry storm to prove it is down")
            .isTrue();
    }

    @Test
    void shouldAdmitExactlyOneProbeAfterCooldownAndCloseOnSuccess() throws Exception {
        // ARRANGE — open the circuit with a cooldown short enough to wait out.
        LlmCircuitBreaker breaker = newBreaker(1, Duration.ofMillis(60), 4);
        failOnce(breaker, LlmFailure.TIMEOUT);
        Assertions.assertThat(breaker.isOpen()).isTrue();
        Thread.sleep(120);

        // ACT
        LlmCircuitBreaker.Admission probe = breaker.tryAcquire();
        LlmCircuitBreaker.Admission secondCaller = breaker.tryAcquire();

        // ASSERT
        Assertions.assertThat(probe.admitted()).isTrue();
        Assertions.assertThat(probe.probe())
            .as("the first caller after the cooldown is the probe")
            .isTrue();
        Assertions.assertThat(secondCaller.admitted())
            .as("a recovering model feels one request, not a thundering herd")
            .isFalse();

        breaker.release(probe);
        breaker.recordSuccess();
        Assertions.assertThat(breaker.isOpen())
            .as("a successful probe closes the circuit")
            .isFalse();
        Assertions.assertThat(breaker.tryAcquire().admitted()).isTrue();
    }

    @Test
    void shouldDoubleTheCooldownWhenTheProbeFails() throws Exception {
        // ARRANGE
        LlmCircuitBreaker breaker = newBreaker(1, Duration.ofMillis(60), 4);
        failOnce(breaker, LlmFailure.TIMEOUT);
        Thread.sleep(120);

        // ACT — the probe goes out and fails, so the model is still not well.
        LlmCircuitBreaker.Admission probe = breaker.tryAcquire();
        Assertions.assertThat(probe.probe()).isTrue();
        breaker.release(probe);
        breaker.recordFailure(LlmFailure.TIMEOUT);

        // ASSERT — re-opened, and for longer: after the original 60ms cooldown has
        // elapsed again the circuit is still shut, because the wait is now 120ms.
        Assertions.assertThat(breaker.isOpen()).isTrue();
        Thread.sleep(80);
        Assertions.assertThat(breaker.isOpen())
            .as("each failed probe backs further off, up to the ceiling")
            .isTrue();
    }

    @Test
    void shouldCapConcurrentCallsAndRefuseOverflowImmediately() {
        // ARRANGE
        LlmCircuitBreaker breaker = newBreaker(3, Duration.ofSeconds(30), 2);

        // ACT — take both permits and ask for a third.
        LlmCircuitBreaker.Admission first = breaker.tryAcquire();
        LlmCircuitBreaker.Admission second = breaker.tryAcquire();
        LlmCircuitBreaker.Admission overflow = breaker.tryAcquire();

        // ASSERT
        Assertions.assertThat(first.admitted()).isTrue();
        Assertions.assertThat(second.admitted()).isTrue();
        Assertions.assertThat(overflow.admitted())
            .as("overflow is refused, not queued behind a slow model (BR-108-9)")
            .isFalse();
        Assertions.assertThat(overflow.outcome()).isEqualTo(SuggestionOutcome.OVERLOADED);

        // Releasing a permit frees the next caller.
        breaker.release(first);
        Assertions.assertThat(breaker.tryAcquire().admitted()).isTrue();
    }

    @Test
    void shouldResetTheFailureStreakOnSuccess() {
        // ARRANGE
        LlmCircuitBreaker breaker = newBreaker(3, Duration.ofSeconds(30), 4);

        // ACT — two failures, then a success, then two more failures.
        failOnce(breaker, LlmFailure.TIMEOUT);
        failOnce(breaker, LlmFailure.TIMEOUT);
        breaker.recordSuccess();
        failOnce(breaker, LlmFailure.TIMEOUT);
        failOnce(breaker, LlmFailure.TIMEOUT);

        // ASSERT — the threshold counts *consecutive* failures, so intermittent
        // ones never accumulate into an outage.
        Assertions.assertThat(breaker.isOpen()).isFalse();
    }

    // --- helpers ---

    private static void failOnce(LlmCircuitBreaker breaker, LlmFailure failure) {
        LlmCircuitBreaker.Admission admission = breaker.tryAcquire();
        breaker.release(admission);
        breaker.recordFailure(failure);
    }

    private static LlmCircuitBreaker newBreaker(int threshold, Duration cooldown, int maxConcurrent) {
        ConfigService config = new ConfigService() {
            @Override
            public boolean isAutotagProviderOpenAi() {
                return false;
            }

            @Override
            public String getAutotagModel() {
                return "gemma2:2b";
            }

            @Override
            public int getAutotagCircuitFailureThreshold() {
                return threshold;
            }

            @Override
            public Duration getAutotagCircuitCooldown() {
                return cooldown;
            }

            @Override
            public Duration getAutotagCircuitCooldownMax() {
                return Duration.ofMinutes(10);
            }

            @Override
            public int getAutotagMaxConcurrent() {
                return maxConcurrent;
            }

            @Override
            public int getAutotagSuggestTimeoutMs() {
                return 8000;
            }
        };
        LlmCircuitBreaker breaker = new LlmCircuitBreaker(config, new SimpleMeterRegistry());
        breaker.init();
        return breaker;
    }
}
