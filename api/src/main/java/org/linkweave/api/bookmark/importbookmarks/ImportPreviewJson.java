package org.linkweave.api.bookmark.importbookmarks;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.linkweave.infrastructure.stereotypes.JaxDTO;

/**
 * Manifest returned by {@code POST /collections/:id/import/preview} (UC-096):
 * the parsed folder/bookmark tree plus totals. Duplicates are flagged per node
 * via {@link ImportNodeJson#duplicate()} rather than a separate URL set, so the
 * client never has to re-normalize URLs to find them. {@code duplicateCount} is
 * a convenience total for the "skip N already in library" pill.
 *
 * <p>Bookmarks with an un-importable URL (e.g. {@code chrome://…}) are excluded
 * from the tree so the selectable count matches what can actually be written;
 * {@code unsupportedCount} reports how many were dropped so the UI can say so.
 * {@code totalBookmarks} counts only the importable bookmarks present in the tree.
 */
@JaxDTO
public record ImportPreviewJson(
    @NotNull @NonNull @Schema(required = true) List<@Valid ImportNodeJson> tree,
    @Schema(required = true) int totalBookmarks,
    @Schema(required = true) int totalFolders,
    @Schema(required = true) int duplicateCount,
    @Schema(required = true) int unsupportedCount
) {
}
