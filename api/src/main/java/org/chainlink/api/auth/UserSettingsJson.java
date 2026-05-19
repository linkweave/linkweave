package org.chainlink.api.auth;

import org.chainlink.infrastructure.stereotypes.JaxDTO;

@JaxDTO
public record UserSettingsJson(
    boolean offlineCachingEnabled
) {}
