package org.linkweave.api.autotag.llm;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    OpenAiClient openAiClient;

    private final ConfigService config;
    private final ObjectMapper objectMapper;

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
        ollamaClient.generate(new OllamaClient.GenerateRequest(
            config.getAutotagModel(), config.getAutotagKeepAlive()));
    }

    // --- Ollama: native structured output via the `format` JSON-Schema enum ---

    private @NonNull List<String> suggestViaOllama(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent) {
        OllamaClient.ChatRequest request = new OllamaClient.ChatRequest(
            config.getAutotagModel(),
            List.of(
                new OllamaClient.Message("system", SYSTEM_PROMPT),
                new OllamaClient.Message("user", bookmarkContent)),
            ollamaConstrainedFormat(vocabulary),
            new OllamaClient.Options(0.0),
            false,
            config.getAutotagKeepAlive());

        OllamaClient.ChatResponse response = ollamaClient.chat(request);
        if (response.message() == null || response.message().content() == null) {
            return List.of();
        }
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
