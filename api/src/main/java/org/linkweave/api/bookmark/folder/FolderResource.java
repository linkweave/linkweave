package org.linkweave.api.bookmark.folder;

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
import org.linkweave.api.bookmark.folder.json.FolderJson;
import org.linkweave.api.bookmark.folder.json.FolderListJson;
import org.linkweave.api.bookmark.folder.json.FolderMoveJson;
import org.linkweave.api.bookmark.folder.json.FolderSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/folders")
public class FolderResource {

    private final FolderService folderService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public FolderListJson getAll(@QueryParam("collectionId") @NotNull @NonNull ID<Collection> collectionId) {
        authorizationService.requireCollectionAccess(collectionId);
        return new FolderListJson(
            folderService.getFoldersByCollection(collectionId).stream()
                .map(FolderMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public FolderJson create(@NotNull @Valid @NonNull FolderSaveJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Folder folder = folderService.createFolder(json);
        return FolderMapper.toJson(folder);
    }

    @PUT
    @Path("/{folderId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public FolderJson rename(
        @PathParam("folderId") @NotNull @NonNull ID<Folder> folderId,
        @NotNull @Valid @NonNull FolderSaveJson json
    ) {
        Folder folder = folderService.getFolder(folderId);
        authorizationService.requireAccessTo(folder);
        Folder renamed = folderService.updateFolder(folderId, json);
        return FolderMapper.toJson(renamed);
    }

    @PATCH
    @Path("/{folderId}/move")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public FolderJson move(
        @PathParam("folderId") @NotNull @NonNull ID<Folder> folderId,
        @NotNull @Valid @NonNull FolderMoveJson json
    ) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Folder moved = folderService.moveFolder(folderId, json.getParentId(), json.getCollectionId());
        return FolderMapper.toJson(moved);
    }

    @DELETE
    @Path("/{folderId}")
    @Authenticated
    public void delete(
        @PathParam("folderId") @NotNull @NonNull ID<Folder> folderId
    ) {
        Folder folder = folderService.getFolder(folderId);
        authorizationService.requireAccessTo(folder);
        folderService.removeFolder(folderId);
    }
}
