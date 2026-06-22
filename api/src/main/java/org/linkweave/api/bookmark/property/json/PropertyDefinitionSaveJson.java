package org.linkweave.api.bookmark.property.json;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.property.PropertyType;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.db.DbConst;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
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

    /**
     * Property names act as identifiers in the search syntax
     * (`property:<name>=…`), so we restrict them to letters, digits,
     * underscore and hyphen — the same character set the frontend
     * tokenizer accepts unquoted. Keep this pattern in sync with the
     * zod schema and the parser in {@code searchQueryProperty.ts}.
     */
    @NotNull
    @NonNull
    @NotBlank
    @Size(max = DbConst.DB_DEFAULT_MAX_LENGTH)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
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
