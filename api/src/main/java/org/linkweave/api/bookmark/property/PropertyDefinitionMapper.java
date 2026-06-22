package org.linkweave.api.bookmark.property;

import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.property.json.PropertyDefinitionJson;
import org.linkweave.api.bookmark.property.json.PropertyDefinitionSaveJson;
import org.linkweave.infrastructure.json.EntityInfoJson;
import org.linkweave.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class PropertyDefinitionMapper {

    @NonNull
    public static PropertyDefinitionJson toJson(@NonNull PropertyDefinition def) {
        return new PropertyDefinitionJson(
            def.getId(),
            EntityInfoJson.fromEntity(def),
            new PropertyDefinitionSaveJson(
                def.getCollection().getId(),
                def.getName(),
                def.getType(),
                def.getAllowedValues(),
                def.getSortOrder()
            )
        );
    }
}
