package org.linkweave.api.bookmark;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.SavedSearchSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.infrastructure.db.UniqueConstraintUtil;
import org.linkweave.infrastructure.stereotypes.Service;
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
        SavedSearch saved = new SavedSearch(
            collectionRepo.referenceById(json.getCollectionId()),
            json.getName(),
            json.getQuery()
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
