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
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class AutoTagRuleSaveJson {

    @NotNull
    @NonNull
    ID<Collection> collectionId;

    @NotNull
    @NonNull
    @NotBlank
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    String pattern;

    /** Comma-separated list of tag names (1..8), e.g. "pr, github". */
    @NotNull
    @NonNull
    @NotBlank
    @Size(max = DbConst.DB_ENUM_LIST_LENGTH)
    String tagNames;

    @Nullable
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    String description;

    @Schema(required = true)
    boolean enabled;
}
