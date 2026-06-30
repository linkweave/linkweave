package org.linkweave.api.autotag.json;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * Describes the active auto-tagging provider so the frontend badge can label
 * itself dynamically instead of hard-coding the model (FR-097): the local Ollama
 * model name when {@code onDevice}, or the hosted model otherwise — keeping the
 * "on-device" claim honest when a hosted provider is configured.
 *
 * <p>Returned by the warm-up endpoint, which the compose form calls on open.
 */
@Value
@AllArgsConstructor
@JaxDTO
public class AutotagLLMProviderJson {

    /** {@code "ollama"} or {@code "openai"}. */
    @NotNull
    @NonNull
    @Schema(required = true)
    String provider;

    /** The model the provider runs, e.g. {@code "gemma2:2b"} or {@code "glm-4.6"}. */
    @NotNull
    @NonNull
    @Schema(required = true)
    String model;

    /** True for local Ollama (data stays on the host); false for a hosted provider. */
    @Schema(required = true)
    boolean onDevice;
}
