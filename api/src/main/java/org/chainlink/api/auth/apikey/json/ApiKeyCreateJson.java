package org.chainlink.api.auth.apikey.json;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class ApiKeyCreateJson {

    @NotBlank
    @Size(max = DbConst.DB_API_KEY_NAME_LENGTH)
    @Schema(required = true)
    @NonNull
    String name;

    @Nullable
    @Pattern(regexp = "^(30d|90d|1y|never)?$", message = "Must be one of: 30d, 90d, 1y, never")
    @Schema(required = false, description = "Expiration period: 30d, 90d, 1y, or never. Defaults to never.")
    String expiresIn;
}
