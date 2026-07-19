package org.linkweave.api.bookmark.folder.json;

import org.linkweave.api.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.shared.sortorder.Placement;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * Explicit drop position for a folder move (UC-102): place the moved folder
 * before or after the anchor, which must be a sibling under the target parent.
 */
@Value
@AllArgsConstructor
@JaxDTO
public class FolderPositionJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Folder> anchorFolderId;

    @NotNull
    @NonNull
    @Schema(required = true)
    Placement placement;
}
