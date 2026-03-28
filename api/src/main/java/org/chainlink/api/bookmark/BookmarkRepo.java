package org.chainlink.api.bookmark;

import java.util.List;
import java.util.Optional;

import ch.dvbern.dvbstarter.types.id.ID;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Repository
public class BookmarkRepo extends BaseRepo<Bookmark> {

    @NonNull
    public List<Bookmark> findAll() {
        return db.findAll(QBookmark.bookmark);
    }

    @NonNull
    public List<Bookmark> findByFolder(@Nullable Folder folder) {
        var query = db.selectFrom(QBookmark.bookmark);
        if (folder != null) {
            query.where(QBookmark.bookmark.folder.eq(folder));
        }
        return query.fetch();
    }

    @NonNull
    public List<Bookmark> findByFolderId(ID<Folder> folderId) {
        return db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.folder.id.eq(folderId.getUUID()))
            .fetch();
    }

    @NonNull
    public List<Bookmark> findByTag(@Nullable Tag tag) {
        var query = db.selectFrom(QBookmark.bookmark);
        if (tag != null) {
            query.where(QBookmark.bookmark.tags.contains(tag));
        }
        return query.fetch();
    }

    @NonNull
    public List<Bookmark> findByTagId(ID<Tag> tagId) {
        return db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.tags.any().id.eq(tagId.getUUID()))
            .fetch();
    }

    @NonNull
    public List<Bookmark> searchByTitle(String searchTerm) {
        return db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.title.likeIgnoreCase("%" + searchTerm + "%"))
            .fetch();
    }
}
