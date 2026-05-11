package org.chainlink.api.cleanup.json;

import java.time.OffsetDateTime;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class CleanupSuggestionJson {

    @NotNull @NonNull @Schema(required = true)
    ID<Bookmark> id;

    @NotNull @NonNull @Schema(required = true)
    String title;

    @NotNull @NonNull @Schema(required = true)
    String url;

    @Nullable @Schema(required = false)
    String folderName;

    @Schema(required = true)
    int clickCount;

    @Nullable @Schema(required = false)
    OffsetDateTime lastClickedAt;

    @NotNull @NonNull @Schema(required = true)
    OffsetDateTime createdAt;

    @Schema(required = true)
    boolean neverClicked;
}
