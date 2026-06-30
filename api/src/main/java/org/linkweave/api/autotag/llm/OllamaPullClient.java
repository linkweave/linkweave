package org.linkweave.api.autotag.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * REST client for Ollama's model-pull endpoint, kept separate from
 * {@link OllamaClient} so it can carry its own (much longer) read-timeout: the
 * stock Ollama image ships no weights, so the first pull downloads the model
 * (gemma2:2b is ~1.6 GB) and can take minutes — far beyond the chat timeout.
 *
 * <p>The pull is idempotent: once the weights are in the {@code ollama-models}
 * volume it returns immediately, so the API can call it before the first
 * warm-up / chat without re-downloading on every request.
 *
 * <p>Configured under {@code quarkus.rest-client.ollama-pull.*} in
 * {@code application.properties}.
 */
@RegisterRestClient(configKey = "ollama-pull")
@Path("/api")
public interface OllamaPullClient {

    @POST
    @Path("/pull")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    PullResponse pull(@NonNull PullRequest request);

    /** {@code stream:false} so the call blocks until the pull completes (or fails). */
    record PullRequest(@NonNull String model, boolean stream) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PullResponse(@Nullable String status) {}
}
