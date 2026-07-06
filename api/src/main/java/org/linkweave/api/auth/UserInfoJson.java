package org.linkweave.api.auth;

import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.Permission;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.stereotypes.JaxDTO;

@JaxDTO
public record UserInfoJson(
    @NotNull @NonNull ID<User> id,
    @NotNull @NonNull String email,
    @NotNull @NonNull String firstName,
    @NotNull @NonNull String lastName,
    @NotNull @NonNull Set<Permission> permissions,
    @NotNull @NonNull ID<Collection> defaultCollectionId,
    @NotNull @NonNull @Valid UserSettingsJson settings
) {
}
