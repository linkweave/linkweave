package org.linkweave.api.bookmark.export_;

import java.time.temporal.ChronoUnit;
import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/export")
public class ExportResource {

    private final BookmarkExportService bookmarkExportService;
    private final BookmarkService bookmarkService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @NonNull
    @Authenticated
    public Response exportBookmarks(
        @PathParam("collectionId") @NotNull @NonNull ID<Collection> collectionId
    ) {
        authorizationService.requireCollectionAccess(collectionId);

        String html = bookmarkExportService.exportBookmarks(collectionId);

        return Response.ok(html)
            .type(MediaType.TEXT_HTML)
            .header("Content-Disposition", "attachment; filename=\"bookmarks.html\"")
            .build();
    }

    /**
     * Exports a subset of a collection's bookmarks (UC-096 export counterpart):
     * the bookmark ids come from the batch selection on the client. Like the
     * full export above, this is a read-only operation guarded by collection
     * access ({@link AuthorizationService#requireCollectionAccess}); in addition
     * every submitted bookmark must belong to the path collection
     * ({@link AuthorizationService#requireSameCollection}) so a caller with
     * access to one collection cannot exfiltrate bookmarks of another.
     */
    @POST
    @Path("/partial")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_HTML)
    @NonNull
    @Authenticated
    public Response exportBookmarksPartial(
        @PathParam("collectionId") @NotNull @NonNull ID<Collection> collectionId,
        @NotNull @Valid @NonNull BookmarkBatchExportJson json
    ) {
        authorizationService.requireCollectionAccess(collectionId);
        // Lenient load: a read-only export degrades gracefully when a selected id
        // was hard-purged (e.g. trashed + "empty trashbin" in another tab) — skip
        // it rather than failing the whole export. requireSameCollection still
        // guards every found bookmark, so cross-collection ids are rejected.
        List<Bookmark> bookmarks = bookmarkService.findBookmarks(json.getBookmarkIds());
        authorizationService.requireSameCollection(bookmarks, collectionId);

        BookmarkExportService.PartialExport export = bookmarkExportService.exportBookmarks(collectionId, bookmarks);

        return Response.ok(export.html())
            .type(MediaType.TEXT_HTML)
            .header("Content-Disposition", "attachment; filename=\"bookmarks.html\"")
            .header("X-Exported-Count", export.exportedCount())
            .build();
    }
}
