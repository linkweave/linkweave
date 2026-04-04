package org.chainlink.api.bookmark.export_;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/export")
public class ExportResource {

    private final BookmarkExportService bookmarkExportService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @NonNull
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
}
