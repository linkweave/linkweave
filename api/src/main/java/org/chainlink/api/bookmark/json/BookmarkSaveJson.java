package org.chainlink.api.bookmark.json;

import java.util.Set;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
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
