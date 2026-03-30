package org.chainlink.api.auth;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

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
    String defaultCollectionId
) {
}
