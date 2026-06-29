package org.linkweave.api.bookmark.importbookmarks;

import java.util.List;

import org.linkweave.api.types.id.ID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.infrastructure.stereotypes.JaxDTO;

/**
 * Body of {@code POST /collections/:id/import/commit} (UC-096). The client sends
 * the <em>pruned</em> selection tree ({@code nodes} — only the kept folders and
 * bookmarks) so the commit is stateless: the server writes what it receives
 * rather than re-parsing a file or caching the preview.
 *
 * <ul>
 *   <li>{@code destinationFolderId} — optional parent folder under which the
 *       imported hierarchy is recreated; {@code null} = collection root.</li>
 *   <li>{@code skipDuplicates} — when true the server recomputes duplicates
 *       against the destination and skips them (defense in depth; the client
 *       has usually already pruned them).</li>
 *   <li>{@code fileName} — the original upload's file name, recorded as the
 *       {@code import-source} provenance property on each created bookmark (when
 *       bookmark properties are enabled), for parity with UC-031.</li>
 * </ul>
 */
@JaxDTO
public record ImportCommitRequestJson(
    @Nullable ID<Folder> destinationFolderId,
    @Schema(required = true) boolean skipDuplicates,
    @Nullable String fileName,
    @NotNull @NonNull @Valid @Schema(required = true) List<ImportNodeJson> nodes
) {
}
