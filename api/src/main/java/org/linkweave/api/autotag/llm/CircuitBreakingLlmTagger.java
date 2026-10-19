package org.linkweave.api.autotag.llm;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

/**
 * Wraps a provider with the protection UC-108 asks for: a model that is not
 * answering costs one call per cooldown, not one per bookmark the user types.
 *
 * <p>Owns what is true of every provider — admission through
 * {@link LlmCircuitBreaker}, classification of a thrown call, and the outcome
 * metric. Classification happens here rather than in the provider because the
 * breaker needs the same answer; what a provider does about it travels back
 * through {@link AbstractLlmTagger#onCallFailed}.
 */
@Slf4j
class CircuitBreakingLlmTagger implements LlmTaggingClient {

    private final AbstractLlmTagger delegate;
    private final LlmCircuitBreaker circuitBreaker;

    CircuitBreakingLlmTagger(
        @NonNull AbstractLlmTagger delegate, @NonNull LlmCircuitBreaker circuitBreaker
    ) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public @NonNull Result suggest(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent, @NonNull String scope) {
        LlmCircuitBreaker.Admission admission = circuitBreaker.tryAcquire(scope);
        if (!admission.admitted()) {
            circuitBreaker.countOutcome(admission.outcome());
            LOG.debug("Skipping model call: {}", admission.outcome());
            return Result.failed(admission.outcome());
        }
        try {
            Result result = delegate.suggest(vocabulary, bookmarkContent, scope);
            // PREPARING means we never reached the model, so it is neither a
            // success that should close the circuit nor a failure that should
            // count towards opening it.
            if (result.outcome() != SuggestionOutcome.PREPARING) {
                circuitBreaker.recordSuccess();
            }
            circuitBreaker.countOutcome(result.outcome());
            return result;
        } catch (RuntimeException e) {
            LlmFailure failure = LlmFailure.classify(e);
            SuggestionOutcome outcome = failure == LlmFailure.TIMEOUT
                ? SuggestionOutcome.TIMEOUT
                : SuggestionOutcome.UNAVAILABLE;
            delegate.onCallFailed(failure, e);
            circuitBreaker.recordFailure(failure);
            circuitBreaker.countOutcome(outcome);
            return Result.failed(outcome);
        } finally {
            circuitBreaker.release(admission);
        }
    }

    @Override
    public void warmUp() {
        delegate.warmUp();
    }
}
