package org.linkweave.api.admin;

import org.linkweave.api.shared.auth.FachRolle;
import org.linkweave.api.shared.user.AuthProvider;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Set;

@Value
@JaxDTO
@AllArgsConstructor
public class AdminUserJson {

    @NotNull
    @NonNull
    ID<User> id;

    @NotNull
    @NonNull
    String email;

    @NotNull
    @NonNull
    String firstName;

    @NotNull
    @NonNull
    String lastName;

    @NotNull
    @NonNull
    Boolean active;

    @Nullable
    AuthProvider authProvider;

    @NotNull
    @NonNull
    Set<FachRolle> roles;
}
