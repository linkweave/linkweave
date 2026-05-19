package org.chainlink.api.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.chainlink.infrastructure.stereotypes.JaxDTO;

@JaxDTO
public record UserSettingsJson(
    @Schema(required = true)
    boolean offlineCachingEnabled
) {}
