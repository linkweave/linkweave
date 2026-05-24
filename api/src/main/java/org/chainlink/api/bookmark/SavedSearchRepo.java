package org.chainlink.api.bookmark;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
public class SavedSearchRepo extends BaseRepo<SavedSearch> {

    public List<SavedSearch> findByCollection(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QSavedSearch.savedSearch)
            .where(QSavedSearch.savedSearch.collection.id.eq(collectionId.getUUID()))
            .orderBy(QSavedSearch.savedSearch.name.lower().asc())
            .fetch();
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        var saved = db.selectFrom(QSavedSearch.savedSearch)
            .where(QSavedSearch.savedSearch.collection.id.eq(collectionId.getUUID()))
            .fetch();
        for (var s : saved) {
            remove(s.getId());
        }
    }
}
