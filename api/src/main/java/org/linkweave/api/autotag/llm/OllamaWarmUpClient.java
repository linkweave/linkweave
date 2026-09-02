package org.linkweave.api.autotag.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.NonNull;

/**
 * REST client for the Ollama warm-up call, kept separate from {@link OllamaClient}
 * so the two can carry different read-timeouts (UC-108 BR-108-1).
 *
 * <p>{@code /api/generate} with a model and no prompt tells Ollama to load the
 * weights into memory and hold them for {@code keep_alive} without running any
 * inference. That load is the expensive part of a cold start — seconds for a 2B
 * model, longer on a loaded host — and paying it here is the whole point: it runs
 * on the {@code ManagedExecutor}, off the request path, so the interactive
 * suggestion that follows finds the model already resident and fits inside the
 * much tighter budget on {@link OllamaClient}.
 *
 * <p>The budget split is the rule, not an accident: the warm-up path may wait
 * ({@code quarkus.rest-client.ollama-warmup.read-timeout}, 120s), the interactive
 * path may not ({@code quarkus.rest-client.ollama.read-timeout}, 8s). Sharing one
 * client would force the interactive call to inherit a cold-start-sized timeout,
 * which is exactly the defect UC-108 was written against.
 */
@RegisterRestClient(configKey = "ollama-warmup")
@Path("/api")
public interface OllamaWarmUpClient {

    @POST
    @Path("/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    GenerateResponse generate(@NonNull GenerateRequest request);

    /** No {@code prompt} field: load the model, don't infer. */
    record GenerateRequest(
        @NonNull String model,
        @JsonProperty("keep_alive") @NonNull String keepAlive
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GenerateResponse(boolean done) {}
}
