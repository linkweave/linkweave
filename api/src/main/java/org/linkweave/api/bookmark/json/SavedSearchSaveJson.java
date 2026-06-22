package org.linkweave.api.bookmark.json;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.db.DbConst;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class SavedSearchSaveJson {

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
    @NotBlank
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    @Schema(required = true)
    String query;
}
