package org.linkweave.api.bookmark.json;

import org.linkweave.api.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.shared.sortorder.Placement;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * Explicit drop position for a bookmark move (UC-103): place the moved bookmark
 * before or after the anchor, which must belong to the target folder group.
 */
@Value
@AllArgsConstructor
@JaxDTO
public class BookmarkPositionJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Bookmark> anchorBookmarkId;

    @NotNull
    @NonNull
    @Schema(required = true)
    Placement placement;
}
