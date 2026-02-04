package org.chainlink.api.bookmark.folder;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepo folderRepo;

    @NonNull
    public Folder createFolder(@NonNull Folder folderToPersist){
         folderRepo.persist(folderToPersist);
         return folderToPersist;
    }

    public Folder getFolder(@NonNull ID<Folder> id) {
        return folderRepo.getById(id);
    }

    public void removeFolder(@NonNull ID<Folder> id) {
        folderRepo.remove(id);
    }
}
