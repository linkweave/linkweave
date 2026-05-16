package org.chainlink.api.bookmark.property.json;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class PropertyDefinitionListJson {
    @NotNull @NonNull List<PropertyDefinitionJson> propertyDefinitions;
}
