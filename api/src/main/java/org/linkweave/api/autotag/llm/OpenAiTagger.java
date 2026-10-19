package org.linkweave.api.autotag.llm;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.shared.config.ConfigService;
import org.jspecify.annotations.NonNull;

/**
 * Tag suggestion through a hosted OpenAI-compatible API, such as z.ai's GLM
 * Coding Plan (FR-097).
 *
 * <p>Carries no model lifecycle at all. There is nothing to download, nothing
 * resident to keep warm and nothing a failed call could invalidate, which is
 * exactly what distinguishes this provider from {@link OllamaTagger}.
 */
@Slf4j
class OpenAiTagger extends AbstractLlmTagger {

    private final ConfigService config;
    private final OpenAiClient openAiClient;

    OpenAiTagger(
        @NonNull ConfigService config,
        @NonNull OpenAiClient openAiClient,
        @NonNull ObjectMapper objectMapper) {
        super(objectMapper);
        this.config = config;
        this.openAiClient = openAiClient;
    }

    @Override
    public @NonNull Result suggest(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent, @NonNull String scope) {
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
     * There is no local state a failure could invalidate, so this only records
     * what happened — under this provider's own name.
     */
    @Override
    void onCallFailed(@NonNull LlmFailure failure, @NonNull RuntimeException cause) {
        LOG.debug("OpenAI chat failed ({}): {}", failure, cause.getMessage());
    }

    /** No-op: a hosted provider has no resident model to preload. */
    @Override
    public void warmUp() {
        // Nothing to warm.
    }
}
