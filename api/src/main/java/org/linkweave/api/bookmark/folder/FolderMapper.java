package org.linkweave.api.bookmark.folder;

import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.folder.json.FolderJson;
import org.linkweave.api.bookmark.folder.json.FolderSaveJson;
import org.linkweave.infrastructure.json.EntityInfoJson;
import org.linkweave.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class FolderMapper {

    @NonNull
    public static FolderJson toJson(@NonNull Folder folder) {
        Folder parent = folder.getParent();
        return new FolderJson(
            folder.getId(),
            EntityInfoJson.fromEntity(folder),
            new FolderSaveJson(
                folder.getCollection().getId(),
                parent != null ? parent.getId() : null,
                folder.getName(),
                folder.getColor()
            ),
            folder.getSortOrder(),
            folder.getDeletedAt()
        );
    }
}
