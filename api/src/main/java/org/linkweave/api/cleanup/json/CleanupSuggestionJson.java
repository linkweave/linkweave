package org.linkweave.api.cleanup.json;

import java.time.OffsetDateTime;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class CleanupSuggestionJson {

    @NotNull @NonNull ID<Bookmark> id;
    @NotNull @NonNull String title;
    @NotNull @NonNull String url;
    @Nullable String folderName;
    @Schema(required = true) int clickCount;
    @Nullable OffsetDateTime lastClickedAt;
    @NotNull @NonNull OffsetDateTime createdAt;
    @Schema(required = true) boolean neverClicked;
}
