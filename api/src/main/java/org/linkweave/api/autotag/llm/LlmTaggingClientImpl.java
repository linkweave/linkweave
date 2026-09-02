package org.linkweave.api.autotag.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.linkweave.api.shared.config.ConfigService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The production {@link LlmTaggingClient}. Dispatches to the configured provider
 * (FR-097): local Ollama (default) or a hosted OpenAI-compatible API such as
 * z.ai's GLM Coding Plan. Each provider reads its own model/credentials from
 * config.
 *
 * <p>Both paths return tag names verbatim from the model; mapping back to
 * {@code Tag} entities and final re-validation against the vocabulary is the
 * service's job, so a provider that ignores the output constraint can't produce
 * tags outside the collection.
 *
 * <p>Every call passes through {@link LlmCircuitBreaker} first (UC-108): a model
 * that is not answering costs one call per cooldown, not one per bookmark the
 * user types.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LlmTaggingClientImpl implements LlmTaggingClient {

    private static final String SYSTEM_PROMPT =
        "You assign tags to a bookmark. Choose only tags from the allowed list that "
        + "clearly describe the bookmark. If none fit, return an empty list. "
        + "Never invent tags or return anything outside the allowed list.";

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

    /** Set once the configured model has been pulled successfully this run. */
    private final AtomicBoolean modelPulled = new AtomicBoolean(false);

    /**
     * Held while a pull is downloading. Lets concurrent callers detect an
     * in-progress pull and fast-fail instead of blocking on the monitor.
     */
    private final AtomicBoolean pullInProgress = new AtomicBoolean(false);

    /** Epoch millis before which no new pull may start (BR-108-6). */
    private final AtomicLong nextPullAllowedEpochMs = new AtomicLong(0);

    /** Current pull backoff, doubling per failed pull up to the configured ceiling. */
    private final AtomicLong pullBackoffMs = new AtomicLong(0);

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
            Result result = config.isAutotagProviderOpenAi()
                ? suggestViaOpenAi(vocabulary, bookmarkContent)
                : suggestViaOllama(vocabulary, bookmarkContent);
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
            handleCallFailure("chat", failure, e);
            circuitBreaker.recordFailure(failure);
            circuitBreaker.countOutcome(outcome);
            return Result.failed(outcome);
        } finally {
            circuitBreaker.release(admission);
        }
    }

    @Override
    public void warmUp() {
        // Hosted providers have no concept of a resident model — warm-up is
        // only meaningful for local Ollama.
        if (config.isAutotagProviderOpenAi()) {
            return;
        }
        if (!ensureModelPulled()) {
            return;
        }
        try {
            ollamaWarmUpClient.generate(new OllamaWarmUpClient.GenerateRequest(
                config.getAutotagModel(), config.getAutotagKeepAlive()));
        } catch (RuntimeException e) {
            handleCallFailure("warm-up generate", LlmFailure.classify(e), e);
            throw e;
        }
    }

    /**
     * Pulls the configured model into Ollama on first use (FR-095). The stock
     * Ollama image ships no weights, so the first warm-up triggers the
     * download — slow once, then cached in the {@code ollama-models} volume.
     *
     * <p>Blocks the calling thread for the download's duration (read-timeout 10
     * minutes, {@code quarkus.rest-client.ollama-pull.read-timeout}), so it must
     * only be invoked off the request path: {@link #warmUp} runs on the service's
     * {@link ManagedExecutor}, and {@link #suggestViaOllama} schedules it via
     * {@link #triggerPullAsync} rather than calling it inline.
     *
     * <p>Returns {@code true} when the model is ready. Only one thread runs the
     * pull at a time; a concurrent caller fails the {@link #pullInProgress}
     * {@code compareAndSet} and fast-fails (returns {@code false}) instead of
     * serializing on a monitor. A pull that fails backs off exponentially
     * (BR-108-6) rather than retrying on the next suggestion, so a broken Ollama
     * cannot be asked to download 1.6 GB once per bookmark.
     */
    private boolean ensureModelPulled() {
        if (modelPulled.get()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (nextPullAllowedEpochMs.get() > now) {
            LOG.debug("Ollama model pull is backing off; skipping");
            return false;
        }
        if (!pullInProgress.compareAndSet(false, true)) {
            LOG.debug("Ollama model pull already in progress; skipping suggestion");
            return false;
        }
        try {
            if (modelPulled.get()) {
                return true;
            }
            String model = config.getAutotagModel();
            LOG.info("Ensuring Ollama model '{}' is pulled; first run may take several minutes...", model);
            ollamaPullClient.pull(new OllamaPullClient.PullRequest(model, false));
            modelPulled.set(true);
            pullBackoffMs.set(0);
            nextPullAllowedEpochMs.set(0);
            LOG.info("Ollama model '{}' is available", model);
            return true;
        } catch (RuntimeException e) {
            backOffPull(e);
            return false;
        } finally {
            pullInProgress.set(false);
        }
    }

    /**
     * Doubles the pull backoff up to its ceiling after a failed download, logging
     * once at WARN because a failed pull is a state change an operator wants to
     * see (BR-108-8).
     */
    private void backOffPull(@NonNull RuntimeException cause) {
        long base = config.getAutotagPullMinInterval().toMillis();
        long ceiling = config.getAutotagPullMinIntervalMax().toMillis();
        long previous = pullBackoffMs.get();
        long next = Math.min(previous == 0 ? base : previous * 2, ceiling);
        pullBackoffMs.set(next);
        nextPullAllowedEpochMs.set(System.currentTimeMillis() + next);
        LOG.warn("Ollama model pull for '{}' failed ({}); next attempt in {}s",
            config.getAutotagModel(), cause.getMessage(), next / 1000);
    }

    /**
     * Kicks off the cold-start pull on the {@link ManagedExecutor} so the request
     * thread is never pinned by the download. Deduped by the {@link #pullInProgress}
     * CAS and rate-limited by the backoff in {@link #ensureModelPulled}.
     */
    private void triggerPullAsync() {
        managedExecutor.execute(() -> {
            try {
                ensureModelPulled();
            } catch (RuntimeException e) {
                LOG.debug("Background Ollama model pull failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Reacts to a failed model call.
     *
     * <p>The one rule that matters here (BR-108-2): only an explicit "model not
     * installed" answer clears {@link #modelPulled} and schedules a pull. This
     * method previously re-pulled after <em>any</em> {@link RuntimeException},
     * so a read timeout — which says the host is too loaded to answer, not that
     * the weights are gone — queued a multi-gigabyte download onto that same
     * host, making the next timeout likelier. That loop is the repeating warning
     * in the 2026-08-28 log.
     */
    private void handleCallFailure(
        @NonNull String operation, @NonNull LlmFailure failure, @NonNull RuntimeException cause) {
        LOG.debug("Ollama {} failed ({}): {}", operation, failure, cause.getMessage());
        if (!failure.warrantsPull()) {
            return;
        }
        if (modelPulled.compareAndSet(true, false)) {
            LOG.warn("Ollama reports model '{}' is not installed; scheduling a pull",
                config.getAutotagModel());
        }
        triggerPullAsync();
    }

    // --- Ollama: native structured output via the `format` JSON-Schema enum ---

    private @NonNull Result suggestViaOllama(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent) {
        if (!modelPulled.get()) {
            triggerPullAsync();
            LOG.debug("Ollama model not warm yet; scheduled pull and skipping this suggestion");
            return Result.failed(SuggestionOutcome.PREPARING);
        }
        OllamaClient.ChatRequest request = new OllamaClient.ChatRequest(
            config.getAutotagModel(),
            List.of(
                new OllamaClient.Message("system", SYSTEM_PROMPT),
                new OllamaClient.Message("user", bookmarkContent)),
            ollamaConstrainedFormat(vocabulary),
            new OllamaClient.Options(0.0),
            false,
            config.getAutotagKeepAlive());

        logModelRequest("Ollama", request);
        OllamaClient.ChatResponse response = ollamaClient.chat(request);
        if (response.message() == null || response.message().content() == null) {
            LOG.debug("Ollama chat response had no message content");
            return Result.of(List.of());
        }
        LOG.debug("Ollama chat response content: {}", response.message().content());
        return Result.of(parseTags(response.message().content()));
    }

    /** JSON Schema pinning {@code tags} to the allowed enum — Ollama honors this strictly. */
    private static @NonNull Map<String, Object> ollamaConstrainedFormat(@NonNull List<String> vocabulary) {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "tags", Map.of(
                    "type", "array",
                    "items", Map.of(
                        "type", "string",
                        "enum", vocabulary))),
            "required", List.of("tags"));
    }

    // --- OpenAI-compatible json_object output + allowed list in the prompt ---

    private @NonNull Result suggestViaOpenAi(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent) {
        String apiKey = config.getAutotagOpenAiApiKey().orElse("");
        if (apiKey.isBlank()) {
            LOG.warn("Autotag provider is 'openai' but linkweave.autotag.openai.api-key is not set");
            return Result.failed(SuggestionOutcome.DISABLED);
        }
        // Not all OpenAI-compatible providers enforce a json_schema enum, so we
        // state the allowed list in the prompt and rely on the service's
        // re-validation to drop anything off-list.
        String userMessage = bookmarkContent
            + "\n\nAllowed tags (choose only from these): " + String.join(", ", vocabulary)
            + "\nRespond with JSON of the form {\"tags\": [\"...\"]}.";

        OpenAiClient.ChatCompletionRequest request = new OpenAiClient.ChatCompletionRequest(
            config.getAutotagOpenAiModel(),
            List.of(
                new OpenAiClient.Message("system", SYSTEM_PROMPT),
                new OpenAiClient.Message("user", userMessage)),
            Map.of("type", "json_object"),
            0.0);

        logModelRequest("OpenAI", request);
        OpenAiClient.ChatCompletionResponse response = openAiClient.complete("Bearer " + apiKey, request);
        if (response.choices() == null || response.choices().isEmpty()) {
            return Result.of(List.of());
        }
        OpenAiClient.Message message = response.choices().get(0).message();
        if (message == null || message.content() == null) {
            return Result.of(List.of());
        }
        return Result.of(parseTags(message.content()));
    }

    /**
     * Debug-logs the exact payload sent to the model so the prompt, vocabulary
     * enum and options can be inspected. Enable with
     * {@code quarkus.log.category."org.linkweave.api.autotag.llm".level=DEBUG}.
     */
    private void logModelRequest(@NonNull String provider, @NonNull Object request) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        try {
            LOG.debug("{} model request: {}", provider, objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            LOG.debug("{} model request (unserializable): {}", provider, request);
        }
    }

    private @NonNull List<String> parseTags(@NonNull String json) {
        try {
            TagsPayload payload = objectMapper.readValue(json, TagsPayload.class);
            return payload.tags() == null ? List.of() : payload.tags();
        } catch (JsonProcessingException e) {
            LOG.debug("Could not parse LLM tag response: {}", e.getMessage());
            return List.of();
        }
    }

    record TagsPayload(@Nullable List<String> tags) {}
}
