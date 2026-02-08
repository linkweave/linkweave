package org.chainlink.api.bookmark.folder;

import java.util.List;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Path("/folderlist")
public class FolderResourceController {

    private final FolderService folderService;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance folderlist(List<Folder> folders);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get() {
        var folders = folderService.getAllFolders();
        return Templates.folderlist(folders);
    }
}
