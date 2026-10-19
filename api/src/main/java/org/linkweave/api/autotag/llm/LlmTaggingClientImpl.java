package org.linkweave.api.autotag.llm;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.linkweave.api.shared.config.ConfigService;
import org.jspecify.annotations.NonNull;

/**
 * The production {@link LlmTaggingClient}. Selects the configured provider
 * (FR-097) — local Ollama by default, or a hosted OpenAI-compatible API such as
 * z.ai's GLM Coding Plan — and wraps whichever answers in the circuit breaker.
 *
 * <p>This class owns only what is true of every provider: admission through
 * {@link LlmCircuitBreaker} (UC-108), classification of a thrown call, and the
 * outcome metric. A model that is not answering therefore costs one call per
 * cooldown, not one per bookmark the user types.
 *
 * <p>Provider-specific work lives in {@link OllamaTagger} and
 * {@link OpenAiTagger}. Both implement {@link LlmTaggingClient} but are
 * constructed here rather than injected, which keeps exactly one CDI bean for
 * the port: selecting a provider needs no qualifier, no alternative and no
 * programmatic lookup. It also keeps Ollama's model-pull state out of the
 * hosted path entirely, where it was previously four unused fields.
 *
 * <p>Both paths return tag names verbatim from the model. Mapping back to
 * {@code Tag} entities and final re-validation against the vocabulary is the
 * service's job, so a provider that ignores the output constraint cannot
 * produce tags outside the collection.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LlmTaggingClientImpl implements LlmTaggingClient {

    @RestClient
    OllamaClient ollamaClient;

    @RestClient
    OllamaWarmUpClient ollamaWarmUpClient;

    @RestClient
    OllamaPullClient ollamaPullClient;

    @RestClient
    OpenAiClient openAiClient;

    private final ConfigService config;
    private final ObjectMapper objectMapper;
    private final ManagedExecutor managedExecutor;
    private final LlmCircuitBreaker circuitBreaker;

    private AbstractLlmTagger ollamaTagger;
    private AbstractLlmTagger openAiTagger;

    /**
     * Builds both providers once the REST clients have been injected. Both are
     * created regardless of which is configured: they are cheap, and holding the
     * pair means a config change needs no re-wiring. Tests that construct this
     * class directly must call this after assigning the client fields, exactly as
     * the container does.
     */
    @PostConstruct
    void initProviders() {
        ollamaTagger = new OllamaTagger(
            config, ollamaClient, ollamaPullClient, ollamaWarmUpClient, objectMapper, managedExecutor);
        openAiTagger = new OpenAiTagger(config, openAiClient, objectMapper);
    }

    private @NonNull AbstractLlmTagger selected() {
        return config.isAutotagProviderOpenAi() ? openAiTagger : ollamaTagger;
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
        AbstractLlmTagger provider = selected();
        try {
            Result result = provider.suggest(vocabulary, bookmarkContent, scope);
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
            // Classification happens here because the breaker needs the same
            // answer; what a provider does about it is the provider's business.
            provider.onCallFailed(failure, e);
            circuitBreaker.recordFailure(failure);
            circuitBreaker.countOutcome(outcome);
            return Result.failed(outcome);
        } finally {
            circuitBreaker.release(admission);
        }
    }

    @Override
    public void warmUp() {
        selected().warmUp();
    }
}
