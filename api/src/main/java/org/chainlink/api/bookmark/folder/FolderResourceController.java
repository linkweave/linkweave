package org.chainlink.api.bookmark.folder;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.folder.json.FolderListJson;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/folders")
public class FolderResourceController {

    private final FolderService folderService;

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
}
