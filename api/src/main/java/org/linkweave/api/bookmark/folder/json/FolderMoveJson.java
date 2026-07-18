package org.linkweave.api.bookmark.folder.json;

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
public class FolderMoveJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Collection> collectionId;

    @Nullable
    @Schema(required = false)
    ID<Folder> parentId;

    /**
     * Explicit drop position among the target parent's children (UC-102).
     * Absent on a plain reparent, which keeps the folder's previous position
     * number (BR-189).
     */
    @Nullable
    @Valid
    @Schema(required = false)
    FolderPositionJson position;
}
