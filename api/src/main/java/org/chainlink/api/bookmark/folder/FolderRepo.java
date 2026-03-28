package org.chainlink.api.bookmark.folder;

import java.util.List;

import lombok.AllArgsConstructor;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.db.Db;
import org.chainlink.infrastructure.stereotypes.Repository;

@Repository
@AllArgsConstructor
public class FolderRepo extends BaseRepo<Folder> {

    private final Db db;

    public List<Folder> findAll() {
        return db.findAll(QFolder.folder);
    }


}
