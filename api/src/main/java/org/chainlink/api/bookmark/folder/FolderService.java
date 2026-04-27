package org.chainlink.api.bookmark.folder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
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
    private final BookmarkRepo bookmarkRepo;



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

    @NonNull
    public List<Folder> getDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        return folderRepo.findDeletedByCollections(collectionIds);
    }

    public long countDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        return folderRepo.countDeletedByCollections(collectionIds);
    }

    public void emptyTrashbin(@NonNull List<ID<Collection>> collectionIds) {
        Set<ID<Folder>> toPurge = folderRepo.findDeletedByCollections(collectionIds).stream()
            .filter(f -> f.getParent() == null || f.getParent().getDeletedAt() == null)
            .map(Folder::getId)
            .collect(Collectors.toSet());
        for (ID<Folder> id : toPurge) {
            folderRepo.findById(id).ifPresent(this::cascadePurge);
        }
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
        Folder folder = folderRepo.getById(id);
        if (folder.getDeletedAt() != null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        cascadeSoftDelete(folder, now);
    }

    private void cascadeSoftDelete(@NonNull Folder folder, @NonNull OffsetDateTime t) {
        for (Bookmark bookmark : bookmarkRepo.findByFolderId(folder.getId())) {
            bookmark.setDeletedAt(t);
            bookmarkRepo.persist(bookmark);
        }
        for (Folder sub : folderRepo.findByParent(folder.getId())) {
            cascadeSoftDelete(sub, t);
        }
        folder.setDeletedAt(t);
        folderRepo.persist(folder);
    }

    @NonNull
    public Folder restoreFolder(@NonNull ID<Folder> id) {
        Folder folder = folderRepo.getById(id);
        if (folder.getDeletedAt() == null) {
            return folder;
        }
        OffsetDateTime cascadeKey = folder.getDeletedAt();
        Folder parent = folder.getParent();
        if (parent != null && parent.getDeletedAt() != null) {
            folder.setParent(null);
        }
        cascadeRestore(folder, cascadeKey);
        return folder;
    }

    private void cascadeRestore(@NonNull Folder folder, @NonNull OffsetDateTime cascadeKey) {
        folder.setDeletedAt(null);
        folderRepo.persist(folder);
        for (Bookmark bookmark : bookmarkRepo.findAllByFolderIncludingDeleted(folder.getId())) {
            if (cascadeKey.equals(bookmark.getDeletedAt())) {
                bookmark.setDeletedAt(null);
                bookmarkRepo.persist(bookmark);
            }
        }
        for (Folder sub : folderRepo.findByParentIncludingDeleted(folder.getId())) {
            if (cascadeKey.equals(sub.getDeletedAt())) {
                cascadeRestore(sub, cascadeKey);
            }
        }
    }

    public void purgeFolder(@NonNull ID<Folder> id) {
        Folder folder = folderRepo.getById(id);
        cascadePurge(folder);
    }

    private void cascadePurge(@NonNull Folder folder) {
        for (Folder sub : folderRepo.findByParentIncludingDeleted(folder.getId())) {
            cascadePurge(sub);
        }
        for (Bookmark bookmark : bookmarkRepo.findAllByFolderIncludingDeleted(folder.getId())) {
            bookmarkRepo.remove(bookmark.getId());
        }
        folderRepo.remove(folder.getId());
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
