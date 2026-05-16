package org.chainlink.api.bookmark.property.json;

import java.math.BigDecimal;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.property.BookmarkPropertyValue;
import org.chainlink.api.bookmark.property.PropertyDefinition;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class BookmarkPropertyValueJson {

    @Nullable
    @Schema(required = false)
    ID<BookmarkPropertyValue> id;

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<PropertyDefinition> definitionId;

    @Nullable
    @Schema(required = false)
    String valueText;

    @Nullable
    @Schema(required = false)
    BigDecimal valueNumber;

    @Schema(required = false)
    boolean valueBoolean;
}
