package org.linkweave.api.autotag.llm;

import java.util.List;

import org.jspecify.annotations.NonNull;

/**
 * Abstraction over the LLM used for tag suggestion. Kept as an interface so
 * {@link BookmarkAutoTagLlmService} can be tested without a running model (swap
 * in a fake), and so the provider (local Ollama vs. a hosted OpenAI-compatible
 * API like z.ai) can change behind config without touching the service.
 *
 * <p>Provider-specific details — which model, keep-alive, API key — are read
 * from config by the implementation, not passed in, so the seam stays
 * provider-agnostic.
 */
public interface LlmTaggingClient {

    /**
     * Returns the subset of {@code vocabulary} the model considers relevant to
     * {@code bookmarkContent}, together with why the list looks the way it does.
     *
     * <p>Does not throw for model or transport failure: degradation is a normal
     * outcome of this call, not an exception, so the reason travels in the
     * {@link Result} where the caller can pass it to the user (UC-108 BR-108-5)
     * instead of having to guess from an empty list.
     *
     * @param scope who this call is for — one user in one collection. Used only
     *     for the concurrency cap: a newer call for the same scope takes over the
     *     older one's slot rather than claiming a second (UC-108 BR-108-9), so a
     *     user typing quickly cannot crowd out other users.
     */
    @NonNull
    Result suggest(
        @NonNull List<String> vocabulary, @NonNull String bookmarkContent, @NonNull String scope);

    /** Preloads the model so a subsequent {@link #suggest} call isn't cold (no-op for hosted providers). */
    void warmUp();

    /**
     * Tag names chosen by the model, and the outcome that produced them. A
     * non-{@link SuggestionOutcome#OK} outcome always carries an empty list.
     */
    record Result(@NonNull List<String> tagNames, @NonNull SuggestionOutcome outcome) {

        public static @NonNull Result of(@NonNull List<String> tagNames) {
            return new Result(tagNames, tagNames.isEmpty() ? SuggestionOutcome.EMPTY : SuggestionOutcome.OK);
        }

        public static @NonNull Result failed(@NonNull SuggestionOutcome outcome) {
            return new Result(List.of(), outcome);
        }
    }
}
