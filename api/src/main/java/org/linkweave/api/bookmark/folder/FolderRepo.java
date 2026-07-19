package org.linkweave.api.bookmark.folder;

import java.util.List;

import org.linkweave.api.types.id.ID;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.AllArgsConstructor;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.db.BaseRepo;
import org.linkweave.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Repository
@AllArgsConstructor
public class FolderRepo extends BaseRepo<Folder> {

    /**
     * Manual order within a sibling group (UC-102): sortOrder first, ties broken
     * deterministically by creation timestamp, then id (BR-191).
     */
    private static final OrderSpecifier<?>[] MANUAL_ORDER = {
        QFolder.folder.sortOrder.asc(),
        QFolder.folder.timestampErstellt.asc(),
        QFolder.folder.id.asc(),
    };

    private static BooleanExpression notDeleted() {
        return QFolder.folder.deletedAt.isNull();
    }

    private static BooleanExpression hasParent(@Nullable ID<Folder> parentId) {
        return parentId == null
            ? QFolder.folder.parent.isNull()
            : QFolder.folder.parent.id.eq(parentId.getUUID());
    }

    private static BooleanExpression deleted() {
        return QFolder.folder.deletedAt.isNotNull();
    }

    public List<Folder> findAll() {
        return db.selectFrom(QFolder.folder)
            .where(notDeleted())
            .fetch();
    }

    @NonNull
    public List<Folder> findByCollection(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QFolder.folder)
            .where(QFolder.folder.collection.id.eq(collectionId.getUUID()).and(notDeleted()))
            .orderBy(MANUAL_ORDER)
            .fetch();
    }

    @NonNull
    public List<Folder> findByParent(@NonNull ID<Folder> parentId) {
        return db.selectFrom(QFolder.folder)
            .where(QFolder.folder.parent.id.eq(parentId.getUUID()).and(notDeleted()))
            .orderBy(MANUAL_ORDER)
            .fetch();
    }

    /**
     * @return the live folders sharing the given parent ({@code null} = root level) in the
     *     given collection, in manual order
     */
    @NonNull
    public List<Folder> findSiblings(@NonNull ID<Collection> collectionId, @Nullable ID<Folder> parentId) {
        return db.selectFrom(QFolder.folder)
            .where(QFolder.folder.collection.id.eq(collectionId.getUUID())
                .and(hasParent(parentId))
                .and(notDeleted()))
            .orderBy(MANUAL_ORDER)
            .fetch();
    }

    /**
     * @return the highest sortOrder in the sibling group ({@code null} = root level), or
     *     {@code null} when the group is empty. Soft-deleted folders count so a restored
     *     folder cannot collide with positions handed out while it sat in the trash.
     */
    @Nullable
    public Long findMaxSortOrderOfSiblings(@NonNull ID<Collection> collectionId, @Nullable ID<Folder> parentId) {
        return db.select(QFolder.folder.sortOrder.max())
            .from(QFolder.folder)
            .where(QFolder.folder.collection.id.eq(collectionId.getUUID())
                .and(hasParent(parentId)))
            .fetchFirst();
    }

    @NonNull
    public List<Folder> findByParentIncludingDeleted(@NonNull ID<Folder> parentId) {
        return db.selectFrom(QFolder.folder)
            .where(QFolder.folder.parent.id.eq(parentId.getUUID()))
            .fetch();
    }

    @NonNull
    public List<Folder> findDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        if (collectionIds.isEmpty()) {
            return List.of();
        }
        var uuids = collectionIds.stream().map(ID::getUUID).toList();
        return db.selectFrom(QFolder.folder)
            .where(QFolder.folder.collection.id.in(uuids).and(deleted()))
            .orderBy(QFolder.folder.deletedAt.desc())
            .fetch();
    }

    public long countDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        if (collectionIds.isEmpty()) {
            return 0L;
        }
        var uuids = collectionIds.stream().map(ID::getUUID).toList();
        Long count = db.select(QFolder.folder.id.count())
            .from(QFolder.folder)
            .where(QFolder.folder.collection.id.in(uuids).and(deleted()))
            .fetchFirst();
        return count != null ? count : 0L;
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        var folders = db.selectFrom(QFolder.folder)
            .where(QFolder.folder.collection.id.eq(collectionId.getUUID()))
            .fetch();
        for (var folder : folders) {
            folder.setParent(null);
        }
        db.flush();
        for (var folder : folders) {
            remove(folder.getId());
        }
    }
}
