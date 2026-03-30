package org.chainlink.api.bookmark.folder;

import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.folder.json.FolderListJson;
import org.chainlink.api.bookmark.folder.json.FolderSaveJson;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

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
    public FolderListJson getAll() {
        return new FolderListJson(
            folderService.getAllFolders().stream()
                .map(FolderMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    public FolderJson create(@NotNull @Valid @NonNull FolderSaveJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Folder folder = folderService.createFolder(json);
        return FolderMapper.toJson(folder);
    }
}
