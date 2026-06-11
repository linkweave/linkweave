package org.chainlink.api.collection.favicon;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;

// SmallRye @RateLimit is a per-method global bucket (not per-user / per-IP), so
// this guard has to accommodate the legitimate fan-out of a fresh page load.
@RateLimit(value = 15000, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/bookmarks/{bookmarkId}/favicon")
public class FaviconResource {

    private final FaviconService faviconService;
    private final BookmarkService bookmarkService;
    private final AuthorizationService authorizationService;

    @GET
    @Authenticated
    public Response getFavicon(
        @PathParam("collectionId") ID<Collection> collectionId,
        @PathParam("bookmarkId") ID<Bookmark> bookmarkId
    ) {
        // Scalar collection-id lookup instead of loading the entity: this
        // endpoint fans out once per visible bookmark, and concurrent entity
        // loads race in Hibernate's shared UTC calendar (HHH-20355).
        ID<Collection> owningCollectionId = bookmarkService.getBookmarkCollectionId(bookmarkId);
        authorizationService.requireCollectionAccess(owningCollectionId);
        authorizationService.requireSameCollection(() -> owningCollectionId, collectionId);
        return faviconService.getFavicon(bookmarkId)
            .map(c -> Response.ok(c.bytes()).header("Content-Type", c.contentType())
                .header("Cache-Control", "private, max-age=86400")
                .build())
            .orElseGet(() -> Response.noContent().build());
    }
}
