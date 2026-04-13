package org.chainlink.api.bookmark.folder;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.AllArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
@AllArgsConstructor
public class FolderRepo extends BaseRepo<Folder> {


    public List<Folder> findAll() {
        return db.findAll(QFolder.folder);
    }

    @NonNull
    public List<Folder> findByCollection(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QFolder.folder)
            .where(QFolder.folder.collection.id.eq(collectionId.getUUID()))
            .fetch();
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        var folders = db.selectFrom(QFolder.folder)
            .where(QFolder.folder.collection.id.eq(collectionId.getUUID()))
            .fetch();
        for (var folder : folders) {
            remove(folder.getId());
        }
    }
}
