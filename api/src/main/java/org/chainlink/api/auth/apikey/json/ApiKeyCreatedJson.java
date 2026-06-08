package org.chainlink.api.auth.apikey.json;

import java.time.OffsetDateTime;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.auth.apikey.ApiKey;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class ApiKeyCreatedJson {

    @NonNull
    @Schema(required = true)
    ID<ApiKey> id;

    @NonNull
    @Schema(required = true)
    String name;

    @NonNull
    @Schema(required = true)
    String prefix;

    @NonNull
    @Schema(required = true)
    OffsetDateTime createdAt;

    @Nullable
    @Schema(required = false)
    OffsetDateTime expiresAt;

    @Nullable
    @Schema(required = false)
    OffsetDateTime lastUsedAt;

    @NonNull
    @Schema(required = true, description = "Full API key. Shown only once.")
    String key;
}
