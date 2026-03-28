package org.chainlink.api.bookmark.folder.json;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class FolderSaveJson {

    @Nullable
    @Schema(required = false)
    ID<Folder> parentId;

    @NotNull
    @NonNull
    @Schema(required = true)
    String name;

}
