package org.chainlink.api.bookmark.folder;

import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.folder.json.FolderSaveJson;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
public class FolderMapper {

    @NonNull
    public static FolderJson toJson(@NonNull Folder folder) {
        return new FolderJson(
            folder.getId(),
            EntityInfoJson.fromEntity(folder),
            new FolderSaveJson(
                folder.parent != null ? folder.parent.getId() : null,
                folder.name
            )
        );
    }
}
