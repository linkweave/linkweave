package org.linkweave.api.bookmark.folder.json;

import org.linkweave.api.types.id.ID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class FolderSaveJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Collection> collectionId;

    @Nullable
    @Schema(required = false)
    ID<Folder> parentId;

    @NotNull
    @NonNull
    @Schema(required = true)
    String name;

    @Nullable
    @Size(max = 7)
    @Schema(required = false)
    String color;
}
