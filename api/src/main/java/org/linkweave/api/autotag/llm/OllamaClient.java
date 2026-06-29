package org.linkweave.api.autotag.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * REST Client for the local Ollama server (FR-095).
 *
 * <p>URL and timeouts are configured under {@code quarkus.rest-client.ollama.*}
 * in {@code application.properties}. {@code keep_alive} controls Ollama's native
 * on-demand load / idle-unload; we never manage the model process ourselves.
 *
 * <p>{@link #chat} is the constrained-tagging call — the {@code format} field
 * carries a JSON Schema whose {@code enum} pins the output to the collection's
 * existing tags. {@link #generate} with no prompt is the warm-up that preloads
 * the model so the first real {@code chat} isn't cold.
 */
@RegisterRestClient(configKey = "ollama")
@Path("/api")
public interface OllamaClient {

    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    ChatResponse chat(@NonNull ChatRequest request);

    @POST
    @Path("/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    GenerateResponse generate(@NonNull GenerateRequest request);

    record ChatRequest(
        @NonNull String model,
        @NonNull List<Message> messages,
        /** JSON Schema constraining the response; an arbitrary object serialized as-is. */
        @NonNull Object format,
        @NonNull Options options,
        boolean stream,
        @JsonProperty("keep_alive") @NonNull String keepAlive
    ) {}

    record Message(@NonNull String role, @NonNull String content) {}

    record Options(double temperature) {}

    record ChatResponse(@Nullable Message message, boolean done) {}

    record GenerateRequest(
        @NonNull String model,
        @JsonProperty("keep_alive") @NonNull String keepAlive
    ) {}

    record GenerateResponse(boolean done) {}
}
