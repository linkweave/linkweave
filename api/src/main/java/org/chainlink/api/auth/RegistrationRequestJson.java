package org.chainlink.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record RegistrationRequestJson(
    @NotBlank
    @NonNull
    String email,

    @NotBlank
    @Size(min = 8, max = 128)
    @NonNull
    String password,

    @NotBlank
    @NonNull
    String vorname,

    @NotBlank
    @NonNull
    String nachname
) {}
