package org.chainlink.api.bookmark;

import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.SavedSearchJson;
import org.chainlink.api.bookmark.json.SavedSearchSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
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

    @NonNull
    public static SavedSearch toEntity(@NonNull Collection collection, @NonNull SavedSearchSaveJson json) {
        return new SavedSearch(collection, json.getName(), json.getQuery());
    }
}
