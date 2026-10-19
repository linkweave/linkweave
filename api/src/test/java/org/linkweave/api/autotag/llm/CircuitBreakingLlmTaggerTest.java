package org.linkweave.api.autotag.llm;

import static org.linkweave.api.autotag.llm.AutotagTestSupport.SCOPE;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.newBreaker;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.testConfig;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.linkweave.api.shared.config.ConfigService;

/**
 * {@link CircuitBreakingLlmTagger} against a stub provider.
 *
 * <p>Covers what the wrapper owns and no provider does: turning a thrown call
 * into a {@link SuggestionOutcome}, telling the provider what the failure was,
 * and refusing to call a provider whose circuit has opened.
 */
class CircuitBreakingLlmTaggerTest {

    private static final ConfigService CONFIG = testConfig(false, Optional.empty(), "glm-4.6");

    @Test
    void shouldReportATimeoutAsTimeoutAndTellTheProvider() {
        // ARRANGE
        StubTagger provider = new StubTagger(() -> {
            throw new RuntimeException("The timeout period of 8000ms has been exceeded");
        });

        // ACT
        LlmTaggingClient.Result result = wrap(provider).suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        Assertions.assertThat(result.outcome()).isEqualTo(SuggestionOutcome.TIMEOUT);
        Assertions.assertThat(result.tagNames()).isEmpty();
        Assertions.assertThat(provider.failures)
            .as("the provider is told what kind of failure it was, so it can react")
            .containsExactly(LlmFailure.TIMEOUT);
    }

    @Test
    void shouldReportAnUnreachableServiceAsUnavailable() {
        // ARRANGE
        StubTagger provider = new StubTagger(() -> {
            throw new RuntimeException(new java.net.ConnectException("Connection refused"));
        });

        // ACT
        LlmTaggingClient.Result result = wrap(provider).suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        Assertions.assertThat(result.outcome()).isEqualTo(SuggestionOutcome.UNAVAILABLE);
        Assertions.assertThat(provider.failures).containsExactly(LlmFailure.UNREACHABLE);
    }

    @Test
    void shouldOpenTheCircuitOnTheFirstRefusedConnection() {
        // ARRANGE
        StubTagger provider = new StubTagger(() -> {
            throw new RuntimeException(new java.net.ConnectException("Connection refused"));
        });
        LlmCircuitBreaker breaker = newBreaker(CONFIG);
        LlmTaggingClient client = new CircuitBreakingLlmTagger(provider, breaker);

        // ACT
        for (int i = 0; i < 5; i++) {
            client.suggest(List.of("rust"), "content", SCOPE);
        }

        // ASSERT
        Assertions.assertThat(breaker.isOpen()).isTrue();
        Assertions.assertThat(provider.calls.get())
            .as("a socket that refuses on contact is conclusive, so it skips the threshold (A3)")
            .isOne();
    }

    @Test
    void shouldOpenTheCircuitOnlyAfterThreeConsecutiveTimeouts() {
        // ARRANGE — a timeout says "busy", not "gone", so it counts to the threshold
        StubTagger provider = new StubTagger(() -> {
            throw new RuntimeException("The timeout period of 8000ms has been exceeded");
        });
        LlmCircuitBreaker breaker = newBreaker(CONFIG);
        LlmTaggingClient client = new CircuitBreakingLlmTagger(provider, breaker);

        // ACT
        for (int i = 0; i < 5; i++) {
            client.suggest(List.of("rust"), "content", SCOPE);
        }

        // ASSERT
        Assertions.assertThat(breaker.isOpen())
            .as("three consecutive failures open the circuit (BR-108-4)")
            .isTrue();
        Assertions.assertThat(provider.calls.get())
            .as("a model that is not answering costs one call per cooldown, not one per bookmark")
            .isEqualTo(3);
    }

    @Test
    void shouldTreatPreparingAsNeitherSuccessNorFailure() {
        // ARRANGE — a cold provider reports PREPARING without reaching the model
        StubTagger provider =
            new StubTagger(() -> LlmTaggingClient.Result.failed(SuggestionOutcome.PREPARING));
        LlmCircuitBreaker breaker = newBreaker(CONFIG);
        LlmTaggingClient client = new CircuitBreakingLlmTagger(provider, breaker);

        // ACT
        for (int i = 0; i < 5; i++) {
            Assertions.assertThat(client.suggest(List.of("rust"), "content", SCOPE).outcome())
                .isEqualTo(SuggestionOutcome.PREPARING);
        }

        // ASSERT — a cold start must not open the circuit, or warming up would
        // lock auto-tagging out for the whole cooldown
        Assertions.assertThat(breaker.isOpen()).isFalse();
        Assertions.assertThat(provider.calls.get()).isEqualTo(5);
    }

    // --- helpers ---

    private static LlmTaggingClient wrap(StubTagger provider) {
        return new CircuitBreakingLlmTagger(provider, newBreaker(CONFIG));
    }

    /** A provider that does whatever the test says, and records what it was told. */
    private static final class StubTagger extends AbstractLlmTagger {

        private final Supplier<LlmTaggingClient.Result> behaviour;
        final AtomicInteger calls = new AtomicInteger();
        final List<LlmFailure> failures = new CopyOnWriteArrayList<>();

        StubTagger(Supplier<LlmTaggingClient.Result> behaviour) {
            super(new ObjectMapper());
            this.behaviour = behaviour;
        }

        @Override
        public LlmTaggingClient.Result suggest(
            List<String> vocabulary, String bookmarkContent, String scope) {
            calls.incrementAndGet();
            return behaviour.get();
        }

        @Override
        public void warmUp() {
            // nothing to warm
        }

        @Override
        void onCallFailed(LlmFailure failure, RuntimeException cause) {
            failures.add(failure);
        }
    }
}
