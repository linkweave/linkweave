package org.chainlink.api.cleanup.json;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class MoveToTrashJson {

    @NotNull @NonNull
    ID<Collection> collectionId;

    @NotNull @NotEmpty @NonNull
    List<ID<Bookmark>> bookmarkIds;
}
