package org.linkweave.api.auth.apikey.json;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class ApiKeyListJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    List<ApiKeyJson> apiKeys;
}
