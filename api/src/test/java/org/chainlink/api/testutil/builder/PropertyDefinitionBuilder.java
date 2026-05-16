package org.chainlink.api.testutil.builder;

import java.util.function.Consumer;

import org.chainlink.api.bookmark.property.PropertyDefinition;
import org.chainlink.api.bookmark.property.PropertyType;
import org.chainlink.api.collection.Collection;
import org.jspecify.annotations.NonNull;

public class PropertyDefinitionBuilder {

    private final PropertyDefinition def;

    public PropertyDefinitionBuilder() {
        this.def = defaultPropertyDefinition();
    }

    @NonNull
    public static PropertyDefinition defaultPropertyDefinition() {
        return new PropertyDefinition(
            CollectionBuilder.defaultCollection(),
            "Test Property",
            PropertyType.TEXT,
            null,
            0
        );

    }

    @NonNull
    public PropertyDefinitionBuilder withCollection(Collection collection) {
        def.setCollection(collection);
        return this;
    }

    @NonNull
    public PropertyDefinitionBuilder withName(String name) {
        def.setName(name);
        return this;
    }

    @NonNull
    public PropertyDefinitionBuilder withType(PropertyType type) {
        def.setType(type);
        return this;
    }

    @NonNull
    public PropertyDefinitionBuilder withAllowedValues(String allowedValues) {
        def.setAllowedValues(allowedValues);
        return this;
    }

    @NonNull
    public PropertyDefinitionBuilder withSortOrder(int sortOrder) {
        def.setSortOrder(sortOrder);
        return this;
    }

    @NonNull
    public static PropertyDefinition build(Consumer<PropertyDefinitionBuilder> block) {
        PropertyDefinitionBuilder builder = new PropertyDefinitionBuilder();
        block.accept(builder);
        return builder.def;
    }
}
