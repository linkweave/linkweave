package org.linkweave.api.bookmark.json;

import java.util.Set;

import org.linkweave.api.types.id.ID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class BookmarkSaveJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Collection> collectionId;

    @Nullable
    @Schema(required = false)
    ID<Folder> folderId;

    @NotNull
    @NonNull
    @NotBlank
    @Schema(required = true)
    String title;

    @NotNull
    @NonNull
    @NotBlank
    @Schema(required = true)
    String url;

    @Nullable
    @Schema(required = false)
    String description;

    @Nullable
    @Schema(required = false)
    Set<ID<Tag>> tagIds;
}
