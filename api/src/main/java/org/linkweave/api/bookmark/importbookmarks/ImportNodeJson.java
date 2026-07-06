package org.linkweave.api.bookmark.importbookmarks;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;

/**
 * One node of the import manifest tree (UC-096). A folder carries
 * {@code children}; a bookmark carries {@code url} (and optionally
 * {@code addDate}, epoch millis). The {@code id} is stable for the duration of
 * one preview→commit cycle and is what the client tracks in its selection set.
 *
 * <p>The same shape is returned by {@code /preview} and sent back (pruned to the
 * kept nodes) on {@code /commit} — the commit is stateless, so the client
 * returns the selected subtree rather than the server caching the parse.
 *
 * <p>{@code duplicate} is computed server-side for bookmark nodes (true when the
 * normalized URL already exists in the destination collection). The client reads
 * this flag directly rather than re-normalizing URLs itself, so duplicate
 * detection can't drift between client and server (UC-096).
 */
@JaxDTO
public record ImportNodeJson(
    @IgnoreForIdClassTest @NotNull @NonNull @Schema(required = true) String id,
    @NotNull @NonNull @Schema(required = true) ImportNodeType type,
    @NotNull @NonNull @Schema(required = true) String name,
    @Nullable String url,
    @Nullable Long addDate,
    @Schema(required = true) boolean duplicate,
    @Nullable List<@Valid ImportNodeJson> children
) {
}
