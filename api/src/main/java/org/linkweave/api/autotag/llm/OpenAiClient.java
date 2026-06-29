package org.linkweave.api.autotag.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * REST Client for an OpenAI-compatible chat-completions API (FR-097) — covers
 * z.ai (GLM), OpenAI, OpenRouter, and similar. Base URL is configured under
 * {@code quarkus.rest-client.openai-autotag.*}; the bearer API key is passed
 * per call (sourced from config, never logged).
 */
@RegisterRestClient(configKey = "openai-autotag")
@Path("/chat/completions")
public interface OpenAiClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    ChatCompletionResponse complete(
        @HeaderParam("Authorization") @NonNull String authorization,
        @NonNull ChatCompletionRequest request);

    record ChatCompletionRequest(
        @NonNull String model,
        @NonNull List<Message> messages,
        /** {@code {"type":"json_object"}} — broadly supported across providers. */
        @JsonProperty("response_format") @NonNull Object responseFormat,
        double temperature
    ) {}

    record Message(@NonNull String role, @NonNull String content) {}

    record ChatCompletionResponse(@Nullable List<Choice> choices) {}

    record Choice(@Nullable Message message) {}
}
