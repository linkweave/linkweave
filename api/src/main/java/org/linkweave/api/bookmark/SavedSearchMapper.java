package org.linkweave.api.bookmark;

import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.SavedSearchJson;
import org.linkweave.api.bookmark.json.SavedSearchSaveJson;
import org.linkweave.infrastructure.json.EntityInfoJson;
import org.linkweave.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class SavedSearchMapper {

    @NonNull
    public static SavedSearchJson toJson(@NonNull SavedSearch savedSearch) {
        return new SavedSearchJson(
            savedSearch.getId(),
            EntityInfoJson.fromEntity(savedSearch),
            new SavedSearchSaveJson(
                savedSearch.getCollection().getId(),
                savedSearch.getName(),
                savedSearch.getQuery()
            )
        );
    }
}
