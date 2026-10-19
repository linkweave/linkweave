package org.linkweave.api.autotag.llm;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Shared base for the provider adapters that sit behind {@link LlmTaggingClient}.
 *
 * <p>Holds only what both providers genuinely share: the system prompt, the
 * response shape, and request logging. Everything a provider does differently —
 * how the output is constrained, whether a model has to be downloaded first,
 * what a failed call should cost — belongs to the subclass.
 *
 * <p>These adapters implement the port directly but are <em>not</em> CDI beans:
 * {@link LlmTaggerProducer} constructs them. That keeps exactly one bean for
 * {@link LlmTaggingClient}, so selecting a provider needs no qualifier, no
 * alternative and no programmatic lookup.
 *
 * <p>The {@code scope} argument of {@link #suggest} is ignored here. It exists
 * for the concurrency cap in {@link LlmCircuitBreaker}, which runs in
 * {@link CircuitBreakingLlmTagger} before any adapter is reached.
 */
@Slf4j
abstract class AbstractLlmTagger implements LlmTaggingClient {

    protected static final String SYSTEM_PROMPT =
        "You assign tags to a bookmark. Choose only tags from the allowed list that "
        + "clearly describe the bookmark. If none fit, return an empty list. "
        + "Never invent tags or return anything outside the allowed list.";

    protected final ObjectMapper objectMapper;

    protected AbstractLlmTagger(@NonNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Lets a provider react to a call that threw, after the caller has classified
     * it. Does nothing by default: for a hosted provider there is no local state
     * a failure could invalidate.
     *
     * <p>Called by {@link CircuitBreakingLlmTagger}, which owns the classification
     * because the circuit breaker needs the same answer.
     */
    void onCallFailed(@NonNull LlmFailure failure, @NonNull RuntimeException cause) {
        // Nothing to do unless the provider keeps local state about the model.
    }

    /**
     * Debug-logs the exact payload sent to the model so the prompt, vocabulary
     * enum and options can be inspected. Enable with
     * {@code quarkus.log.category."org.linkweave.api.autotag.llm".level=DEBUG}.
     */
    protected void logModelRequest(@NonNull String provider, @NonNull Object request) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        try {
            LOG.debug("{} model request: {}", provider, objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            LOG.debug("{} model request (unserializable): {}", provider, request);
        }
    }

    /** Both providers are asked for {@code {"tags": [...]}}; an unparseable reply means no suggestions. */
    protected @NonNull List<String> parseTags(@NonNull String json) {
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
