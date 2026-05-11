package org.chainlink.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@JaxDTO
public record RegistrationRequestJson(
    @NotBlank @NotNull @NonNull @Schema(required = true) String email,
    @NotBlank @NotNull @Size(min = 8, max = 128) @NonNull @Schema(required = true) String password,
    @NotBlank @NotNull @NonNull @Schema(required = true) String vorname,
    @NotBlank @NotNull @NonNull @Schema(required = true) String nachname
) {}
