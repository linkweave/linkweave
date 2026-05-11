package org.chainlink.api.trashbin;

import java.util.List;
import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkMapper;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderMapper;
import org.chainlink.api.bookmark.folder.FolderService;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionService;
import org.chainlink.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/trashbin")
public class TrashbinResource {

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final AuthorizationService authorizationService;
    private final CollectionService collectionService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public TrashbinJson list() {
        List<ID<Collection>> collectionIds = userCollectionIds();
        return new TrashbinJson(
            bookmarkService.getDeletedByCollections(collectionIds).stream()
                .map(BookmarkMapper::toJson)
                .toList(),
            folderService.getDeletedByCollections(collectionIds).stream()
                .map(FolderMapper::toJson)
                .toList()
        );
    }

    @GET
    @Path("/count")
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public TrashbinCountJson count() {
        List<ID<Collection>> collectionIds = userCollectionIds();
        long total = bookmarkService.countDeletedByCollections(collectionIds)
            + folderService.countDeletedByCollections(collectionIds);
        return new TrashbinCountJson(total);
    }

    @POST
    @Path("/bookmarks/{bookmarkId}/restore")
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public BookmarkJson restoreBookmark(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId
    ) {
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        return BookmarkMapper.toJson(bookmarkService.restoreBookmark(bookmarkId));
    }

    @DELETE
    @Path("/bookmarks/{bookmarkId}")
    @Authenticated
    public void purgeBookmark(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId
    ) {
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        bookmarkService.purgeBookmark(bookmarkId);
    }

    @POST
    @Path("/folders/{folderId}/restore")
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public FolderJson restoreFolder(
        @PathParam("folderId") @NotNull @NonNull ID<Folder> folderId
    ) {
        Folder folder = folderService.getFolder(folderId);
        authorizationService.requireAccessTo(folder);
        return FolderMapper.toJson(folderService.restoreFolder(folderId));
    }

    @DELETE
    @Path("/folders/{folderId}")
    @Authenticated
    public void purgeFolder(
        @PathParam("folderId") @NotNull @NonNull ID<Folder> folderId
    ) {
        Folder folder = folderService.getFolder(folderId);
        authorizationService.requireAccessTo(folder);
        folderService.purgeFolder(folderId);
    }

    @DELETE
    @Authenticated
    public void empty() {
        List<ID<Collection>> collectionIds = userCollectionIds();
        folderService.emptyTrashbin(collectionIds);
        bookmarkService.emptyTrashbin(collectionIds);
    }

    @NonNull
    private List<ID<Collection>> userCollectionIds() {
        return collectionService.findCollectionIdsForCurrentUser();
    }
}
