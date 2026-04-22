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
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepo folderRepo;
    private final CollectionRepo collectionRepo;



    @NonNull
    public Folder createFolder(@NonNull FolderSaveJson json) {
        ID<Collection> collectionId = json.getCollectionId();

        Folder folder = new Folder();
        folder.setCollection(collectionRepo.referenceById(collectionId));
        folder.setName(json.getName());
        folder.setColor(json.getColor());

        ID<Folder> parentId = json.getParentId();
        if (parentId != null) {
            Folder parent = folderRepo.getById(parentId);
            requireFolderBelongsToCollection(parent, collectionId);
            folder.setParent(parent);
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

    @NonNull
    public Folder updateFolder(@NonNull ID<Folder> id, @NonNull FolderSaveJson json) {
        Folder folder = folderRepo.getById(id);

        ID<Collection> collectionId = json.getCollectionId();
        ID<Folder> parentId = json.getParentId();

        if (parentId != null) {
            Folder parent = folderRepo.getById(parentId);
            requireFolderBelongsToCollection(parent, collectionId);
            requireNotAncestor(id, parent);
            folder.setParent(parent);
        } else {
            folder.setParent(null);
        }

        folder.setCollection(collectionRepo.referenceById(collectionId));
        folder.setName(json.getName());
        folder.setColor(json.getColor());

        folderRepo.persist(folder);
        return folder;
    }

    @NonNull
    public Folder moveFolder(@NonNull ID<Folder> id, @Nullable ID<Folder> newParentId, @NonNull ID<Collection> collectionId) {
        Folder folder = folderRepo.getById(id);

        if (newParentId != null) {
            Folder parent = folderRepo.getById(newParentId);
            requireFolderBelongsToCollection(parent, collectionId);
            requireNotAncestor(id, parent);
            folder.setParent(parent);
        } else {
            folder.setParent(null);
        }

        folderRepo.persist(folder);
        return folder;
    }

    public void removeFolder(@NonNull ID<Folder> id) {
        folderRepo.remove(id);
    }

    private void requireFolderBelongsToCollection(@NonNull Folder folder, @NonNull ID<Collection> collectionId) {
        if (!folder.getCollection().getId().equals(collectionId)) {
            throw new AppFailureException(
                AppFailureMessage.internalError("Folder does not belong to the specified collection")
            );
        }
    }

    private void requireNotAncestor(@NonNull ID<Folder> folderId, @NonNull Folder candidate) {
        Folder current = candidate;
        while (current != null) {
            if (current.getId().equals(folderId)) {
                throw new AppFailureException(
                    AppFailureMessage.internalError("Cannot move a folder into itself or one of its descendants")
                );
            }
            current = current.getParent();
        }
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        folderRepo.deleteByCollection(collectionId);
    }
}
