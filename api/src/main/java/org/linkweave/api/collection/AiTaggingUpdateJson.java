package org.linkweave.api.collection;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.linkweave.infrastructure.stereotypes.JaxDTO;

/**
 * Request body for the per-collection AI-tagging toggle (UC-112).
 *
 * <p>A single field on its own endpoint rather than another column on
 * {@link CollectionUpdateJson}: that request is owner-or-admin because it also
 * carries the collection's name and fetch allowlist, while this setting is
 * changeable by any member (BR-112-5). Folding it in would have forced one of the
 * two authorization rules onto the other.
 */
@Value
@JaxDTO
@AllArgsConstructor
public class AiTaggingUpdateJson {

    @Schema(required = true) boolean enabled;
}
