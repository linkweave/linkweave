package org.chainlink.api.bookmark.folder;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.AllArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
@AllArgsConstructor
public class FolderRepo extends BaseRepo<Folder> {

    private static BooleanExpression notDeleted() {
        return QFolder.folder.deletedAt.isNull();
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
            .fetch();
    }

    @NonNull
    public List<Folder> findByParent(@NonNull ID<Folder> parentId) {
        return db.selectFrom(QFolder.folder)
            .where(QFolder.folder.parent.id.eq(parentId.getUUID()).and(notDeleted()))
            .fetch();
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
