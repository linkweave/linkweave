package org.linkweave.api.bookmark.property.json;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Value
@AllArgsConstructor
@JaxDTO
public class PropertyDefinitionUsageJson {
    @Schema(required = true)
    long affectedBookmarks;
}
