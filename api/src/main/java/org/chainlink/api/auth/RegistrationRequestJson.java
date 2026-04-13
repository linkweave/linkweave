package org.chainlink.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record RegistrationRequestJson(
    @NotBlank
    @NotNull
    @NonNull
    String email,

    @NotBlank
    @NotNull
    @Size(min = 8, max = 128)
    @NonNull
    String password,

    @NotBlank
    @NotNull
    @NonNull
    String vorname,

    @NotBlank
    @NotNull
    @NonNull
    String nachname
) {}
