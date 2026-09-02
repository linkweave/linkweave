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

    /**
     * Whether AI tag suggestions run at all here (UC-112 BR-112-7).
     *
     * <p>False when the operator has switched the feature off (FR-096) or a member
     * has switched it off for this collection (FR-105) — the client is told the
     * answer, not which of the two produced it. When false the dialog renders no
     * AI group at all: there is nothing to retry and nothing to wait for, so a
     * placeholder would be clutter in a dialog the user passes through constantly.
     */
    @Schema(required = true)
    boolean enabled;

    /**
     * Whether the client should request suggestions automatically as the user
     * types, or only when they ask (UC-108 BR-108-7).
     *
     * <p>Server-side because it is an operator decision, not a user preference:
     * it was a hard-coded {@code AUTO_FIRE} constant in the frontend, so a host
     * too small to run the model at interactive speed had no way to keep the
     * feature on demand short of shipping a patched bundle. The dialog already
     * calls warm-up on open, so this rides along on a response it was fetching
     * anyway.
     */
    @Schema(required = true)
    boolean autoFire;

    /**
     * The interactive budget in milliseconds (BR-108-1). The client aborts its
     * own request on the same budget, so a server-side hang cannot leave the
     * dialog spinning after the server has already given up.
     */
    @Schema(required = true)
    int suggestTimeoutMs;
}
