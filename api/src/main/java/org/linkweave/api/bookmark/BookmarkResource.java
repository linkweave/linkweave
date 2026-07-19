package org.linkweave.api.bookmark;

import java.time.temporal.ChronoUnit;
import java.util.List;

import org.linkweave.api.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.BookmarkBatchDeleteJson;
import org.linkweave.api.bookmark.json.BookmarkBatchMoveJson;
import org.linkweave.api.bookmark.json.BookmarkBatchTagJson;
import org.linkweave.api.bookmark.json.BookmarkJson;
import org.linkweave.api.bookmark.json.BookmarkListJson;
import org.linkweave.api.bookmark.json.BookmarkMoveJson;
import org.linkweave.api.bookmark.json.BookmarkSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.linkweave.infrastructure.db.RetryOnSqliteBusy;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@RetryOnSqliteBusy(attempts = 8)
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

    @GET
    @Path("/{bookmarkId}")
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public BookmarkJson get(@PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId) {
        Bookmark bookmark = loadBookmarkOr404(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        return BookmarkMapper.toJson(bookmark);
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
        // Also guard the bookmark's CURRENT collection — access to the target
        // collection alone would let a caller pull any foreign bookmark into
        // their own collection by id.
        Bookmark existing = loadBookmarkOr404(bookmarkId);
        authorizationService.requireAccessTo(existing);
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
        Bookmark bookmark = loadBookmarkOr404(bookmarkId);
        authorizationService.requireSameCollection(bookmark, json.getCollectionId());
        bookmarkService.moveToFolder(bookmark, json.getFolderId(), json.getCollectionId(), json.getPosition());
        return BookmarkMapper.toJson(bookmark);
    }

    @DELETE
    @Path("/{bookmarkId}")
    @Authenticated
    public void delete(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId
    ) {
        Bookmark bookmark = loadBookmarkOr404(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        bookmarkService.removeBookmark(bookmarkId);
    }

    @POST
    @Path("/batch-move")
    @RetryOnSqliteBusy(attempts = 12)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @RolesAllowed("BOOKMARK_WRITE")
    public BookmarkListJson batchMove(@NotNull @Valid @NonNull BookmarkBatchMoveJson json) {
        List<Bookmark> bookmarks = authorizeAndLoad(json.getCollectionId(), json.getBookmarkIds());
        bookmarkService.batchMoveToFolder(bookmarks, json.getFolderId(), json.getCollectionId());
        return toListJson(bookmarks);
    }

    @POST
    @Path("/batch-delete")
    @RetryOnSqliteBusy(attempts = 12)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("BOOKMARK_WRITE")
    public void batchDelete(@NotNull @Valid @NonNull BookmarkBatchDeleteJson json) {
        List<Bookmark> bookmarks = authorizeAndLoad(json.getCollectionId(), json.getBookmarkIds());
        bookmarkService.batchRemove(bookmarks);
    }

    @POST
    @Path("/batch-tag")
    @RetryOnSqliteBusy(attempts = 12)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @RolesAllowed("BOOKMARK_WRITE")
    public BookmarkListJson batchTag(@NotNull @Valid @NonNull BookmarkBatchTagJson json) {
        List<Bookmark> bookmarks = authorizeAndLoad(json.getCollectionId(), json.getBookmarkIds());
        bookmarkService.batchEditTags(
            bookmarks, json.getAddTagIds(), json.getRemoveTagIds(), json.getCollectionId());
        return toListJson(bookmarks);
    }

    /**
     * Shared batch guard: collection access plus the every-bookmark-belongs-to-it
     * check (without it, access to one collection would allow mutating bookmarks
     * of any other).
     */
    @NonNull
    private List<Bookmark> authorizeAndLoad(
        @NonNull ID<Collection> collectionId,
        @NonNull List<ID<Bookmark>> bookmarkIds
    ) {
        authorizationService.requireCollectionAccess(collectionId);
        List<Bookmark> bookmarks = bookmarkService.getBookmarks(bookmarkIds);
        authorizationService.requireSameCollection(bookmarks, collectionId);
        return bookmarks;
    }

    @NonNull
    private static BookmarkListJson toListJson(@NonNull List<Bookmark> bookmarks) {
        return new BookmarkListJson(bookmarks.stream().map(BookmarkMapper::toJson).toList());
    }

    /**
     * Missing single bookmarks are a 404 for API clients (UC-079 A5), not the
     * 500 that {@code getById}'s AppFailureException would produce.
     * <p>
     * Accepted risk: unknown ids answer 404 while existing-but-foreign ids
     * answer 403, which distinguishes "doesn't exist" from "not yours"
     * (unlike {@code requireApiKeyOwnership}, which deliberately collapses
     * the two). Bookmark ids are random UUIDs, so existence probing is
     * impractical, and UC-079 A4/A5 mandate the distinct messages.
     */
    @NonNull
    private Bookmark loadBookmarkOr404(@NonNull ID<Bookmark> bookmarkId) {
        return bookmarkService.findBookmark(bookmarkId)
            .orElseThrow(() -> new NotFoundException("Bookmark not found: " + bookmarkId));
    }

    @POST
    @Path("/{bookmarkId}/track-click")
    @Authenticated
    public void trackClick(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId
    ) {
        Bookmark bookmark = loadBookmarkOr404(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        bookmarkService.trackClick(bookmarkId);
    }
}
