package org.chainlink.api.bookmark.property;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
public class PropertyDefinitionRepo extends BaseRepo<PropertyDefinition> {

    public List<PropertyDefinition> findByCollection(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QPropertyDefinition.propertyDefinition)
            .where(QPropertyDefinition.propertyDefinition.collection.id.eq(collectionId.getUUID()))
            .orderBy(QPropertyDefinition.propertyDefinition.sortOrder.asc())
            .fetch();
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        var definitions = findByCollection(collectionId);
        for (var def : definitions) {
            remove(def.getId());
        }
    }
}
