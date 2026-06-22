package org.linkweave.api.auth;

import java.util.Set;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record UserInfoJson(
    @NotNull @NonNull String email,
    @NotNull @NonNull String firstName,
    @NotNull @NonNull String lastName,
    @NotNull @NonNull Set<String> roles,
    @NotNull @NonNull ID<Collection> defaultCollectionId,
    @NotNull @NonNull @Valid UserSettingsJson settings
) {
}
