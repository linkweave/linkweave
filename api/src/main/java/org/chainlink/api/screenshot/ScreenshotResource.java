package org.chainlink.api.screenshot;

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
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxResource;

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
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        authorizationService.requireSameCollection(bookmark, collectionId);
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
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        authorizationService.requireSameCollection(bookmark, collectionId);
        screenshotService.refreshScreenshot(bookmarkId);
        return Response.noContent().build();
    }
}
