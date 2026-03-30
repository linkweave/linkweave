package org.chainlink.api.auth;

import java.util.Set;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record UserInfoJson(
    @NotNull
    @NonNull
    String email,

    @NotNull
    @NonNull
    String firstName,

    @NotNull
    @NonNull
    String lastName,

    @NotNull
    @NonNull
    Set<String> roles,

    @NonNull
    ID<Collection> defaultCollectionId
) {
}
