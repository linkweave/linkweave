package org.chainlink.api.bookmark.json;

import java.time.OffsetDateTime;
import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.property.json.BookmarkPropertyValueJson;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class BookmarkJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Bookmark> id;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    EntityInfoJson entityInfo;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    BookmarkSaveJson data;

    @NotNull
    @NonNull
    @Schema(required = true)
    Integer clickCount;

    @Nullable
    @Schema(required = false)
    OffsetDateTime lastClickedAt;

    @Nullable
    @Schema(required = false)
    OffsetDateTime deletedAt;

    @NotNull
    @NonNull
    @Schema(required = true)
    List<BookmarkPropertyValueJson> propertyValues;
}
