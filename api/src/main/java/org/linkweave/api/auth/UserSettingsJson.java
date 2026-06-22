package org.linkweave.api.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.linkweave.infrastructure.stereotypes.JaxDTO;

@JaxDTO
public record UserSettingsJson(
    @Schema(required = true)
    boolean offlineCachingEnabled,
    @Schema(required = true)
    boolean savedSearchesEnabled
) {}
