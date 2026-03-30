package org.chainlink.api.bookmark.folder;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.folder.json.FolderSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepo folderRepo;
    private final CollectionRepo collectionRepo;

    @NonNull
    public List<Folder> getAllFolders() {
        return folderRepo.findAll();
    }

    @NonNull
    public Folder createFolder(@NonNull FolderSaveJson json) {
        ID<Collection> collectionId = json.getCollectionId();

        Folder folder = new Folder();
        folder.collection = collectionRepo.referenceById(collectionId);
        folder.name = json.getName();

        ID<Folder> parentId = json.getParentId();
        if (parentId != null) {
            Folder parent = folderRepo.getById(parentId);
            if (!parent.collection.getId().equals(collectionId)) {
                throw new AppFailureException(
                    AppFailureMessage.internalError("Parent folder does not belong to the same collection")
                );
            }
            folder.parent = parent;
        }

        folderRepo.persist(folder);
        return folder;
    }

    @NonNull
    public List<Folder> getFoldersByCollection(@NonNull ID<Collection> collectionId) {
        return folderRepo.findByCollection(collectionId);
    }

    public Folder getFolder(@NonNull ID<Folder> id) {
        return folderRepo.getById(id);
    }

    public void removeFolder(@NonNull ID<Folder> id) {
        folderRepo.remove(id);
    }
}
