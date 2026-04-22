package org.chainlink.api.bookmark.folder;

import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.folder.json.FolderJson;
import org.chainlink.api.bookmark.folder.json.FolderSaveJson;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class FolderMapper {

    @NonNull
    public static FolderJson toJson(@NonNull Folder folder) {
        return new FolderJson(
            folder.getId(),
            EntityInfoJson.fromEntity(folder),
            new FolderSaveJson(
                folder.getCollection().getId(),
                folder.getParent() != null ? folder.getParent().getId() : null,
                folder.getName(),
                folder.getColor()
            )
        );
    }
}
