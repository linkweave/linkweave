package org.chainlink.api.bookmark.property.json;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.property.PropertyType;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class PropertyDefinitionSaveJson {

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

    @NotNull
    @NonNull
    @Schema(required = true)
    PropertyType type;

    @Nullable
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    @Schema(required = false)
    String allowedValues;

    @Schema(required = true)
    int sortOrder;
}
