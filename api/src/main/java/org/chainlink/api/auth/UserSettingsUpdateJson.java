package org.chainlink.api.auth;

import jakarta.validation.constraints.NotNull;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record UserSettingsUpdateJson(
    @NotNull @NonNull Boolean offlineCachingEnabled
) {}
