package org.chainlink.api.bookmark;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.json.BookmarkListJson;
import org.chainlink.api.bookmark.json.BookmarkSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

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
    public BookmarkJson create(@NotNull @Valid @NonNull BookmarkSaveJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Bookmark bookmark = bookmarkService.createBookmark(json);
        return BookmarkMapper.toJson(bookmark);
    }
}
