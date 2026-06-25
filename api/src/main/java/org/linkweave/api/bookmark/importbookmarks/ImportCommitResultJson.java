package org.linkweave.api.bookmark.importbookmarks;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.linkweave.infrastructure.stereotypes.JaxDTO;

/**
 * Result of {@code POST /collections/:id/import/commit} (UC-096).
 *
 * <p>{@code duplicatesSkipped} is distinct from UC-031's
 * {@link ImportSummaryJson#bookmarksSkipped} (which counts invalid-URL skips) —
 * here it counts bookmarks dropped because their URL already existed in the
 * destination and {@code skipDuplicates} was on.
 */
@JaxDTO
public record ImportCommitResultJson(
    @Schema(required = true) int imported,
    @Schema(required = true) int foldersCreated,
    @Schema(required = true) int duplicatesSkipped
) {
}
