package org.linkweave.api.autotag.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
    OllamaPullClient ollamaPullClient;

    @RestClient
    OpenAiClient openAiClient;

    private final ConfigService config;
    private final ObjectMapper objectMapper;
    private final ManagedExecutor managedExecutor;

    /** Set once the configured model has been pulled successfully this run. */
    private final AtomicBoolean modelPulled = new AtomicBoolean(false);

    /**
     * Held while a pull is downloading. Lets concurrent callers detect an
     * in-progress pull and fast-fail instead of blocking on the monitor.
     */
    private final AtomicBoolean pullInProgress = new AtomicBoolean(false);

    @Override
    public @NonNull List<String> suggest(@NonNull List<String> vocabulary, @NonNull String bookmarkContent) {
        return config.isAutotagProviderOpenAi()
            ? suggestViaOpenAi(vocabulary, bookmarkContent)
            : suggestViaOllama(vocabulary, bookmarkContent);
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
            ollamaClient.generate(new OllamaClient.GenerateRequest(
                config.getAutotagModel(), config.getAutotagKeepAlive()));
        } catch (RuntimeException e) {
            invalidateModelAfterFailure("warm-up generate", e);
            throw e;
        }
    }

    /**
     * Pulls the configured model into Ollama on first use (FR-095). The stock
     * Ollama image ships no weights, so the first warm-up triggers the
     * download — slow once, then cached in the {@code ollama-models} volume.
     *
     * <p>Blocks the calling thread for the download's full duration (read-timeout
     * 30 minutes, {@code quarkus.rest-client.ollama-pull.read-timeout}), so it
     * must only be invoked off the request path: {@link #warmUp} runs on the
     * service's {@link ManagedExecutor}, and {@link #suggestViaOllama} schedules
     * it via {@link #triggerPullAsync} rather than calling it inline.
     *
     * <p>Returns {@code true} when the model is ready. Only one thread runs the
     * pull at a time; a concurrent caller fails the {@link #pullInProgress}
     * {@code compareAndSet} and fast-fails (returns {@code false}) instead of
     * serializing on a monitor. The flag is set only on success, so a failed
     * pull (server not up yet) is retried next time.
     */
    private boolean ensureModelPulled() {
        if (modelPulled.get()) {
            return true;
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
            LOG.info("Ollama model '{}' is available", model);
            return true;
        } finally {
            pullInProgress.set(false);
        }
    }

    /**
     * Kicks off the cold-start pull on the {@link ManagedExecutor} so the request
     * thread is never pinned by the download. Deduped by the {@link #pullInProgress}
     * CAS in {@link #ensureModelPulled}, so firing it on every cold suggestion is
     * cheap; a pull failure is swallowed and retried on the next attempt.
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
     * Clears {@link #modelPulled} and re-arms the background pull after a
     * chat/generate call fails. Ollama can lose a model that was pulled
     * successfully earlier, this will trigger a repull
     */
    private void invalidateModelAfterFailure(@NonNull String operation, @NonNull RuntimeException cause) {
        if (modelPulled.compareAndSet(true, false)) {
            LOG.warn("Ollama {} failed ({}); re-pulling model '{}' before the next suggestion",
                operation, cause.getMessage(), config.getAutotagModel());
            triggerPullAsync();
        }
    }

    // --- Ollama: native structured output via the `format` JSON-Schema enum ---

    private @NonNull List<String> suggestViaOllama(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent) {
        if (!modelPulled.get()) {
            triggerPullAsync();
            LOG.debug("Ollama model not warm yet; scheduled pull and skipping this suggestion");
            return List.of();
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
        OllamaClient.ChatResponse response;
        try {
            response = ollamaClient.chat(request);
        } catch (RuntimeException e) {
            invalidateModelAfterFailure("chat", e);
            throw e;
        }
        if (response.message() == null || response.message().content() == null) {
            LOG.debug("Ollama chat response had no message content");
            return List.of();
        }
        LOG.debug("Ollama chat response content: {}", response.message().content());
        return parseTags(response.message().content());
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

    private @NonNull List<String> suggestViaOpenAi(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent) {
        String apiKey = config.getAutotagOpenAiApiKey().orElse("");
        if (apiKey.isBlank()) {
            LOG.warn("Autotag provider is 'openai' but linkweave.autotag.openai.api-key is not set");
            return List.of();
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
            return List.of();
        }
        OpenAiClient.Message message = response.choices().get(0).message();
        if (message == null || message.content() == null) {
            return List.of();
        }
        return parseTags(message.content());
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
