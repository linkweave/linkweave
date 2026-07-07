package org.linkweave.api.admin;

import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Returned to the supporter after a successful password reset. The plaintext
 * password is shown once so the supporter can relay it to the user — it is
 * never persisted in cleartext.
 */
@Value
@JaxDTO
@AllArgsConstructor
public class PasswordResetResultJson {

    @NotNull
    @NonNull
    String newPassword;
}
