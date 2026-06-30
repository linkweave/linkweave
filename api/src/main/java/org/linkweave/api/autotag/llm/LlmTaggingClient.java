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
     * {@code bookmarkContent}. Implementations constrain the model to the
     * vocabulary as far as the provider allows. Best-effort: a model/transport
     * failure surfaces as a {@link RuntimeException} for the caller to swallow.
     */
    @NonNull
    List<String> suggest(@NonNull List<String> vocabulary, @NonNull String bookmarkContent);

    /** Preloads the model so a subsequent {@link #suggest} call isn't cold (no-op for hosted providers). */
    void warmUp();
}
