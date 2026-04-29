package org.chainlink.api.collection.favicon;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/bookmarks/{bookmarkId}/favicon")
public class FaviconResource {

    private final FaviconService faviconService;
    private final AuthorizationService authorizationService;

    @GET
    public Response getFavicon(
        @PathParam("collectionId") ID<Collection> collectionId,
        @PathParam("bookmarkId") ID<Bookmark> bookmarkId
    ) {
        authorizationService.requireCollectionAccess(collectionId);
        return faviconService.getFavicon(bookmarkId)
            .map(c -> Response.ok(c.bytes()).header("Content-Type", c.contentType())
                .header("Cache-Control", "private, max-age=86400")
                .build())
            .orElseGet(() -> Response.noContent().build());
    }
}
