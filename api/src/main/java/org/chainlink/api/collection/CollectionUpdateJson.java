package org.chainlink.api.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionUpdateJson {

    @NotBlank @NotNull @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH) @NonNull @Schema(required = true)
    String name;

    @Size(max = DbConst.DB_TEXTAREA_MAX_LENGTH_2000) @Nullable @Schema(required = false)
    String faviconAllowlist;
}
