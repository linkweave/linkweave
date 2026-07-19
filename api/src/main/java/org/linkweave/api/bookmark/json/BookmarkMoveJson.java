package org.linkweave.api.bookmark.json;

import org.linkweave.api.types.id.ID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class BookmarkMoveJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Collection> collectionId;

    @Nullable
    @Schema(required = false)
    ID<Folder> folderId;

    /**
     * Explicit drop position among the target folder group's bookmarks (UC-103).
     * Absent on a plain move, which keeps the bookmark's previous position
     * number (BR-195).
     */
    @Nullable
    @Valid
    @Schema(required = false)
    BookmarkPositionJson position;
}
