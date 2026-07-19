package org.linkweave.api.bookmark.folder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.linkweave.infrastructure.clock.AppClock;
import org.linkweave.api.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.bookmark.folder.json.FolderPositionJson;
import org.linkweave.api.bookmark.folder.json.FolderSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.shared.sortorder.Placement;
import org.linkweave.api.shared.sortorder.SortOrderPlacement;
import org.linkweave.api.shared.sortorder.SparseSortOrder;
import org.linkweave.infrastructure.errorhandling.AppFailureException;
import org.linkweave.infrastructure.errorhandling.AppFailureMessage;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepo folderRepo;
    private final CollectionRepo collectionRepo;
    private final BookmarkRepo bookmarkRepo;
    private final AppClock appClock;



    @NonNull
    public Folder createFolder(@NonNull FolderSaveJson json) {
        ID<Collection> collectionId = json.getCollectionId();

        ID<Folder> parentId = json.getParentId();
        Folder parent = null;
        if (parentId != null) {
            parent = folderRepo.getById(parentId);
            requireFolderBelongsToCollection(parent, collectionId);
        }

        Folder folder = new Folder(
            collectionRepo.referenceById(collectionId),
            parent,
            json.getName(),
            json.getColor(),
            SparseSortOrder.afterMax(folderRepo.findMaxSortOrderOfSiblings(collectionId, parentId)),
            null
        );

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
    public Folder moveFolder(
        @NonNull ID<Folder> id,
        @Nullable ID<Folder> newParentId,
        @NonNull ID<Collection> collectionId,
        @Nullable FolderPositionJson position
    ) {
        Folder folder = folderRepo.getById(id);

        if (newParentId != null) {
            Folder parent = folderRepo.getById(newParentId);
            requireFolderBelongsToCollection(parent, collectionId);
            requireNotAncestor(id, parent);
            folder.setParent(parent);
        } else {
            folder.setParent(null);
        }

        // Without an explicit position the folder keeps its number and is ranked
        // among its new siblings by it (BR-189).
        if (position != null) {
            placeAmongSiblings(folder, newParentId, collectionId, position);
        }

        folderRepo.persist(folder);
        return folder;
    }

    /**
     * Assigns {@code folder} the sort order for the drop position described by
     * {@code position}: usually a midpoint between the two neighbors; when their
     * gap is exhausted, the whole sibling group is renumbered in steps of
     * {@link SparseSortOrder#STEP} (BR-187).
     */
    private void placeAmongSiblings(
        @NonNull Folder folder,
        @Nullable ID<Folder> parentId,
        @NonNull ID<Collection> collectionId,
        @NonNull FolderPositionJson position
    ) {
        ID<Folder> anchorId = position.getAnchorFolderId();
        if (anchorId.equals(folder.getId())) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.FOLDER_POSITION_ANCHOR_SELF")
            );
        }
        Folder anchor = folderRepo.getById(anchorId);
        requireFolderBelongsToCollection(anchor, collectionId);

        List<Folder> siblings = new ArrayList<>(folderRepo.findSiblings(collectionId, parentId));
        siblings.removeIf(f -> f.getId().equals(folder.getId()));

        int anchorIndex = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(anchorId)) {
                anchorIndex = i;
                break;
            }
        }
        if (anchorIndex < 0) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.FOLDER_POSITION_ANCHOR_NOT_SIBLING")
            );
        }
        // insert in place of anchor or right after it
        int insertIndex = position.getPlacement() == Placement.BEFORE ? anchorIndex : anchorIndex + 1;
        SortOrderPlacement.placeAt(siblings, folder, insertIndex, folderRepo::persist);
    }

    public void removeFolder(@NonNull ID<Folder> id) {
        Folder folder = folderRepo.getById(id);
        if (folder.getDeletedAt() != null) {
            return;
        }
        OffsetDateTime now = appClock.offsetDateTime().now();
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
