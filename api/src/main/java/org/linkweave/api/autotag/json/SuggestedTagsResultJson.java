package org.linkweave.api.autotag.json;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.json.TagJson;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * The suggestion endpoints' response: the tags, plus why the list looks the way
 * it does (UC-108 BR-108-5).
 *
 * <p>Replaces a bare {@code TagListJson}. The list alone cannot distinguish an
 * empty answer from a missing one, and the difference is the whole point of
 * UC-108 — one is a result the user should accept and move on from, the other is
 * a degraded feature they deserve to be told about in a single quiet line.
 */
@Value
@AllArgsConstructor
@JaxDTO
public class SuggestedTagsResultJson {

    @NotNull
    @NonNull
    @Schema(required = true)
    List<TagJson> tagList;

    @NotNull
    @NonNull
    @Schema(required = true)
    SuggestionStatusJson status;
}
