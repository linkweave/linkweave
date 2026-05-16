package org.chainlink.api.bookmark.property;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.property.json.PropertyDefinitionSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class PropertyDefinitionService {

    private final PropertyDefinitionRepo propertyDefinitionRepo;
    private final CollectionRepo collectionRepo;

    @NonNull
    public PropertyDefinition create(@NonNull PropertyDefinitionSaveJson json) {
        PropertyDefinition def = new PropertyDefinition();
        def.setCollection(collectionRepo.referenceById(json.getCollectionId()));
        def.setName(json.getName());
        def.setType(json.getType());
        def.setAllowedValues(json.getAllowedValues());
        def.setSortOrder(json.getSortOrder());
        propertyDefinitionRepo.persistAndFlush(def);
        return def;
    }

    @NonNull
    public PropertyDefinition getById(@NonNull ID<PropertyDefinition> id) {
        return propertyDefinitionRepo.getById(id);
    }

    @NonNull
    public PropertyDefinition update(@NonNull PropertyDefinition def, @NonNull PropertyDefinitionSaveJson json) {
        if (!def.getCollectionId().equals(json.getCollectionId())) {
            throw new AppValidationException(AppValidationMessage.invalidEntityRelation(
                def.getId(),
                def.getCollectionId(),
                json.getCollectionId()
            ));
        }
        def.setName(json.getName());
        def.setType(json.getType());
        def.setAllowedValues(json.getAllowedValues());
        def.setSortOrder(json.getSortOrder());
        return def;
    }

    public void remove(@NonNull ID<PropertyDefinition> id) {
        propertyDefinitionRepo.remove(id);
    }

    @NonNull
    public List<PropertyDefinition> findByCollection(@NonNull ID<Collection> collectionId) {
        return propertyDefinitionRepo.findByCollection(collectionId);
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        propertyDefinitionRepo.deleteByCollection(collectionId);
    }
}
