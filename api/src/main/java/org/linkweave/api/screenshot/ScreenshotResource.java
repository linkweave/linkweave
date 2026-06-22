package org.linkweave.api.screenshot;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import org.linkweave.infrastructure.stereotypes.JaxResource;

@RateLimit(value = 15000, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot")
public class ScreenshotResource {

    private final ScreenshotService screenshotService;
    private final BookmarkService bookmarkService;
    private final AuthorizationService authorizationService;

    @GET
    @Authenticated
    public Response getScreenshot(
        @PathParam("collectionId") ID<Collection> collectionId,
        @PathParam("bookmarkId") ID<Bookmark> bookmarkId
    ) {
        // Scalar collection-id lookup instead of loading the entity: this
        // endpoint fans out once per visible bookmark, and concurrent entity
        // loads race in Hibernate's shared UTC calendar (HHH-20355).
        ID<Collection> owningCollectionId = bookmarkService.getBookmarkCollectionId(bookmarkId);
        authorizationService.requireCollectionAccess(owningCollectionId);
        authorizationService.requireSameCollection(owningCollectionId, collectionId);
        return screenshotService.getScreenshot(bookmarkId)
            .map(c -> Response.ok(c.bytes())
                .header("Content-Type", c.contentType())
                .header("Cache-Control", "private, max-age=86400")
                .build())
            .orElseGet(() -> Response.noContent().build());
    }

    @POST
    @Path("/refresh")
    @Authenticated
    public Response refreshScreenshot(
        @PathParam("collectionId") ID<Collection> collectionId,
        @PathParam("bookmarkId") ID<Bookmark> bookmarkId
    ) {
        ID<Collection> owningCollectionId = bookmarkService.getBookmarkCollectionId(bookmarkId);
        authorizationService.requireCollectionAccess(owningCollectionId);
        authorizationService.requireSameCollection(owningCollectionId, collectionId);
        screenshotService.refreshScreenshot(bookmarkId);
        return Response.noContent().build();
    }
}
