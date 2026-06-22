package org.linkweave.api.trashbin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.folder.json.FolderJson;
import org.linkweave.api.bookmark.json.BookmarkJson;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class TrashbinJson {

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    List<BookmarkJson> bookmarks;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    List<FolderJson> folders;
}
