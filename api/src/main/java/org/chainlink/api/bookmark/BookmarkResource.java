package org.chainlink.api.bookmark;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.json.BookmarkListJson;
import org.chainlink.api.bookmark.json.BookmarkMoveJson;
import org.chainlink.api.bookmark.json.BookmarkSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/bookmarks")
public class BookmarkResource {

    private final BookmarkService bookmarkService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public BookmarkListJson list(@QueryParam("collectionId") @NotNull @NonNull ID<Collection> collectionId) {
        authorizationService.requireCollectionAccess(collectionId);
        return new BookmarkListJson(
            bookmarkService.getBookmarksByCollection(collectionId).stream()
                .map(BookmarkMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public BookmarkJson create(@NotNull @Valid @NonNull BookmarkSaveJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Bookmark bookmark = bookmarkService.createBookmark(json);
        return BookmarkMapper.toJson(bookmark);
    }

    @PUT
    @Path("/{bookmarkId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public BookmarkJson update(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId,
        @NotNull @Valid @NonNull BookmarkSaveJson json
    ) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Bookmark bookmark = bookmarkService.updateBookmark(bookmarkId, json);
        return BookmarkMapper.toJson(bookmark);
    }

    @PATCH
    @Path("/{bookmarkId}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public BookmarkJson move(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId,
        @NotNull @Valid @NonNull BookmarkMoveJson json
    ) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Bookmark bookmark = bookmarkService.moveBookmarkToFolder(bookmarkId, json);
        return BookmarkMapper.toJson(bookmark);
    }

    @DELETE
    @Path("/{bookmarkId}")
    @Authenticated
    public void delete(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId
    ) {
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        bookmarkService.removeBookmark(bookmarkId);
    }

    @POST
    @Path("/{bookmarkId}/track-click")
    @Authenticated
    public void trackClick(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId
    ) {
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        bookmarkService.trackClick(bookmarkId);
    }
}
