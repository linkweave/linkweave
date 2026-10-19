package org.linkweave.api.autotag.llm;

import static org.linkweave.api.autotag.llm.AutotagTestSupport.SCOPE;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.openAiClient;
import static org.linkweave.api.autotag.llm.AutotagTestSupport.testConfig;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.linkweave.api.autotag.llm.AutotagTestSupport.OpenAiState;

/**
 * {@link OpenAiTagger} against a hosted OpenAI-compatible API (FR-097).
 *
 * <p>Tested bare, with no circuit breaker and no Ollama plumbing: this provider
 * has no model lifecycle, so every outcome these tests assert is produced by the
 * tagger itself.
 */
class OpenAiTaggerTest {

    @Test
    void shouldReturnTagsFromHostedProviderWithBearerHeaderAndPrompt() {
        // ARRANGE
        OpenAiState openAi = new OpenAiState();
        openAi.response = new OpenAiClient.ChatCompletionResponse(List.of(
            new OpenAiClient.Choice(
                new OpenAiClient.Message("assistant", "{\"tags\":[\"rust\",\"databases\"]}"))));
        OpenAiTagger tagger = newHosted(openAi, Optional.of("test-key"), "glm-4.6");

        // ACT
        LlmTaggingClient.Result result =
            tagger.suggest(List.of("rust", "databases", "career"), "Async Rust blog", SCOPE);

        // ASSERT
        Assertions.assertThat(result.tagNames())
            .as("tags parsed from the hosted provider's choices")
            .containsExactly("rust", "databases");
        Assertions.assertThat(openAi.count.get())
            .as("the hosted provider is called exactly once")
            .isOne();
        Assertions.assertThat(openAi.authorization)
            .as("the API key is sent as a Bearer token")
            .isEqualTo("Bearer test-key");
        Assertions.assertThat(openAi.lastRequest.model())
            .as("the configured hosted model is used")
            .isEqualTo("glm-4.6");
        Assertions.assertThat(openAi.lastRequest.messages()).hasSize(2);
        Assertions.assertThat(openAi.lastRequest.messages().get(1).content())
            .as("the allowed vocabulary and JSON contract are stated in the prompt")
            .contains("Allowed tags", "rust", "databases", "career");
    }

    @Test
    void shouldReturnEmptyAndNotCallProviderWhenApiKeyIsBlank() {
        // ARRANGE
        OpenAiState openAi = new OpenAiState();
        OpenAiTagger tagger = newHosted(openAi, Optional.empty(), "glm-4.6");

        // ACT
        LlmTaggingClient.Result result = tagger.suggest(List.of("rust"), "content", SCOPE);

        // ASSERT
        Assertions.assertThat(result.tagNames())
            .as("a missing API key fast-fails with an empty list")
            .isEmpty();
        Assertions.assertThat(result.outcome())
            .as("an unconfigured provider is reported as disabled, not as a failure")
            .isEqualTo(SuggestionOutcome.DISABLED);
        Assertions.assertThat(openAi.count.get())
            .as("the provider must not be called without an API key")
            .isZero();
    }

    @Test
    void shouldReturnEmptyWhenHostedResponseHasNoUsableContent() {
        // ARRANGE
        OpenAiState openAi = new OpenAiState();
        OpenAiTagger tagger = newHosted(openAi, Optional.of("k"), "glm-4.6");

        // ACT / ASSERT — each degenerate response yields an empty list, never throwing
        assertEmptyFor(openAi, tagger, new OpenAiClient.ChatCompletionResponse(null));
        assertEmptyFor(openAi, tagger, new OpenAiClient.ChatCompletionResponse(List.of()));
        assertEmptyFor(openAi, tagger, new OpenAiClient.ChatCompletionResponse(
            List.of(new OpenAiClient.Choice(null))));
    }

    // --- helpers ---

    private static OpenAiTagger newHosted(OpenAiState state, Optional<String> apiKey, String model) {
        return new OpenAiTagger(
            testConfig(true, apiKey, model), openAiClient(state), new ObjectMapper());
    }

    private static void assertEmptyFor(
        OpenAiState openAi, OpenAiTagger tagger, OpenAiClient.ChatCompletionResponse response) {
        openAi.response = response;
        Assertions.assertThat(tagger.suggest(List.of("rust"), "content", SCOPE).tagNames())
            .as("a degenerate hosted response yields an empty list")
            .isEmpty();
    }
}
