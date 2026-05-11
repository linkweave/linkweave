package org.chainlink.api.auth;

import java.util.Set;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record UserInfoJson(
    @NotNull @NonNull @Schema(required = true) String email,
    @NotNull @NonNull @Schema(required = true) String firstName,
    @NotNull @NonNull @Schema(required = true) String lastName,
    @NotNull @NonNull @Schema(required = true) Set<String> roles,
    @NotNull @NonNull @Schema(required = true) ID<Collection> defaultCollectionId
) {
}
