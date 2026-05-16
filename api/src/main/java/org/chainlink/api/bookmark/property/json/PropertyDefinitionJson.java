package org.chainlink.api.bookmark.property.json;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.chainlink.api.bookmark.property.PropertyDefinition;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Value
@AllArgsConstructor
@JaxDTO
public class PropertyDefinitionJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    ID<PropertyDefinition> id;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    EntityInfoJson entityInfo;

    @NotNull
    @NonNull
    @Valid
    @Schema(required = true)
    PropertyDefinitionSaveJson data;
}
