package org.linkweave.api.autotag.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.linkweave.api.shared.config.ConfigService;
import org.jspecify.annotations.NonNull;

/**
 * Composition root for tag suggestion: picks the configured provider (FR-097)
 * and wraps it in the circuit breaker.
 *
 * <p>The choice is made once, when the bean is created, because
 * {@code linkweave.autotag.provider} is injected config and cannot change while
 * the application runs. Only the selected provider is constructed, so the one
 * that is not in use holds no REST clients and no state.
 *
 * <p>Naming concrete adapters is this class's job and nothing else's, which is
 * why {@code PortsAndAdaptersTest} exempts producers from the rule that forbids
 * depending on an adapter.
 */
@ApplicationScoped
@Slf4j
class LlmTaggerProducer {

    @RestClient
    OllamaClient ollamaClient;

    @RestClient
    OllamaWarmUpClient ollamaWarmUpClient;

    @RestClient
    OllamaPullClient ollamaPullClient;

    @RestClient
    OpenAiClient openAiClient;

    @Produces
    @ApplicationScoped
    @NonNull
    LlmTaggingClient llmTaggingClient(
        @NonNull ConfigService config,
        @NonNull ObjectMapper objectMapper,
        @NonNull ManagedExecutor managedExecutor,
        @NonNull LlmCircuitBreaker circuitBreaker
    ) {
        boolean hosted = config.isAutotagProviderOpenAi();
        AbstractLlmTagger provider = hosted
            ? new OpenAiTagger(config, openAiClient, objectMapper)
            : new OllamaTagger(
                config, ollamaClient, ollamaPullClient, ollamaWarmUpClient, objectMapper, managedExecutor);
        LOG.info("Autotag provider: {}", hosted ? "openai" : "ollama");
        return new CircuitBreakingLlmTagger(provider, circuitBreaker);
    }
}
