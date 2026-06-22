package org.linkweave.api.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.linkweave.infrastructure.db.DbConst;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@JaxDTO
@AllArgsConstructor
public class CollectionUpdateJson {

    @NotBlank @NotNull @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH) @NonNull
    String name;

    @Size(max = DbConst.DB_TEXTAREA_MAX_LENGTH_2000) @Nullable
    String browserFetchAllowlist;

    @Schema(required = true) boolean screenshotEnabled;
}
