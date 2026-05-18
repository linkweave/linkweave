package org.chainlink.api.bookmark.property;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.QBookmark;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
public class BookmarkPropertyValueRepo extends BaseRepo<BookmarkPropertyValue> {

    public List<BookmarkPropertyValue> findByBookmark(@NonNull ID<Bookmark> bookmarkId) {
        QBookmarkPropertyValue pv = QBookmarkPropertyValue.bookmarkPropertyValue;
        QBookmark b = QBookmark.bookmark;
        return db.selectFrom(pv)
            .innerJoin(pv.bookmark, b)
            .where(b.id.eq(bookmarkId.getUUID()))
            .fetch();
    }

    public List<BookmarkPropertyValue> findByCollection(@NonNull ID<Collection> collectionId) {
        QBookmarkPropertyValue pv = QBookmarkPropertyValue.bookmarkPropertyValue;
        QBookmark b = QBookmark.bookmark;
        return db.selectFrom(pv)
            .innerJoin(pv.bookmark, b)
            .where(b.collection.id.eq(collectionId.getUUID()))
            .fetch();
    }

    public void deleteByBookmark(@NonNull ID<Bookmark> bookmarkId) {
        var values = findByBookmark(bookmarkId);
        for (var v : values) {
            remove(v.getId());
        }
    }

    public long countByPropertyDefinition(@NonNull ID<PropertyDefinition> definitionId) {
        QBookmarkPropertyValue pv = QBookmarkPropertyValue.bookmarkPropertyValue;
        Long count = db.select(pv.count())
            .from(pv)
            .where(pv.propertyDefinition.id.eq(definitionId.getUUID()))
            .fetchFirst();
        return count == null ? 0L : count;
    }

    public void deleteByPropertyDefinition(@NonNull ID<PropertyDefinition> definitionId) {
        QBookmarkPropertyValue pv = QBookmarkPropertyValue.bookmarkPropertyValue;
        db.delete(pv)
            .where(pv.propertyDefinition.id.eq(definitionId.getUUID()))
            .execute();
    }

    public void flush() {
        db.flush();
    }
}
