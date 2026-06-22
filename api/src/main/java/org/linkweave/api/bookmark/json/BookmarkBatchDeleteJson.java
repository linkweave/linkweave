package org.linkweave.api.bookmark.json;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class BookmarkBatchDeleteJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<Collection> collectionId;

    @NotNull
    @NotEmpty
    @Size(max = 500)
    @NonNull
    @Schema(required = true)
    List<ID<Bookmark>> bookmarkIds;
}
