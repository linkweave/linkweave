package org.chainlink.api.bookmark.json;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class TagSaveJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Collection> collectionId;

    @NotNull
    @NonNull
    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Schema(required = true)
    String name;

    @Nullable
    @Size(max = 7, min = 7)
    @Schema(required = false)
    String color;
}
