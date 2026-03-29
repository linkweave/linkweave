package org.chainlink.api.bookmark.folder;

import java.util.List;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/folders")
public class FolderResourceController {

    private final FolderService folderService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Folder> getAll() {
        return folderService.getAllFolders();
    }
}
