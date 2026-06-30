package org.linkweave.api.autotag.json;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Request body for suggesting tags on unsaved compose-form text — lets the
 * frontend ask for suggestions before the bookmark is persisted. All fields are
 * optional; the model classifies on whatever text is present.
 */
@Value
@AllArgsConstructor
@JaxDTO
public class SuggestTagsJson {

    @Nullable
    @Schema(required = false)
    String title;

    @Nullable
    @Schema(required = false)
    String url;

    @Nullable
    @Schema(required = false)
    String description;
}
