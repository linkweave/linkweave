package org.chainlink.api.bookmark;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.SavedSearchSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.infrastructure.db.UniqueConstraintUtil;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private static final String CONSTRAINT_NAME = "uc_saved_search_name_collection";
    private static final String CONSTRAINT_COLUMNS = "SavedSearch.name, SavedSearch.collection_id";

    private final SavedSearchRepo savedSearchRepo;
    private final CollectionRepo collectionRepo;

    @NonNull
    public SavedSearch createSavedSearch(@NonNull SavedSearchSaveJson json) {
        SavedSearch saved = SavedSearchMapper.toEntity(
            collectionRepo.referenceById(json.getCollectionId()),
            json
        );
        upsertAndFlush(saved);
        return saved;
    }

    @NonNull
    public SavedSearch getSavedSearch(@NonNull ID<SavedSearch> id) {
        return savedSearchRepo.getById(id);
    }

    @NonNull
    public SavedSearch updateSavedSearch(@NonNull SavedSearch saved, @NonNull SavedSearchSaveJson json) {
        // Collection is intentionally not updated — the resource layer enforces
        // that the supplied collectionId matches the existing one.
        saved.setName(json.getName());
        saved.setQuery(json.getQuery());
        upsertAndFlush(saved);
        return saved;
    }

    private void upsertAndFlush(SavedSearch saved) {
        UniqueConstraintUtil.persistAndHandleUnique(
            () -> savedSearchRepo.persistAndFlush(saved),
            CONSTRAINT_NAME,
            CONSTRAINT_COLUMNS,
            "AppValidation.uq_saved_search_name_collection"
        );
    }

    public void removeSavedSearch(@NonNull ID<SavedSearch> id) {
        savedSearchRepo.remove(id);
    }

    public List<SavedSearch> findByCollection(@NonNull ID<Collection> collectionId) {
        return savedSearchRepo.findByCollection(collectionId);
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        savedSearchRepo.deleteByCollection(collectionId);
    }
}
