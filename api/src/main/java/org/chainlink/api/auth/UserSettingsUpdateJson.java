package org.chainlink.api.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record UserSettingsUpdateJson(
    @NotNull @NonNull
    @Schema(required = true)
    boolean offlineCachingEnabled,
    @NotNull @NonNull
    @Schema(required = true)
    boolean savedSearchesEnabled
) {}
