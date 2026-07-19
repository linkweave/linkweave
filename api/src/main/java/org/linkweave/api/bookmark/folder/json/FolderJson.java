package org.linkweave.api.bookmark.folder.json;

import java.time.OffsetDateTime;

import org.linkweave.api.types.id.ID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.infrastructure.json.EntityInfoJson;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Value
@AllArgsConstructor
@JaxDTO
public class FolderJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Folder> id;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    EntityInfoJson entityInfo;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    FolderSaveJson data;

    /**
     * Manual position among siblings (UC-102). Server-managed: assigned on
     * create/move, never taken from client input.
     */
    @Schema(required = true)
    long sortOrder;

    @Nullable
    @Schema(required = false)
    OffsetDateTime deletedAt;
}
