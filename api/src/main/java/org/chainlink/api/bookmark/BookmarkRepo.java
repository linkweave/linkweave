package org.chainlink.api.bookmark;

import java.time.OffsetDateTime;
import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.collection.Collection;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Repository
public class BookmarkRepo extends BaseRepo<Bookmark> {

    private static BooleanExpression notDeleted() {
        return QBookmark.bookmark.deletedAt.isNull();
    }

    private static BooleanExpression deleted() {
        return QBookmark.bookmark.deletedAt.isNotNull();
    }

    @NonNull
    public List<Bookmark> findAll() {
        return db.selectFrom(QBookmark.bookmark)
            .where(notDeleted())
            .fetch();
    }

    @NonNull
    public List<Bookmark> findByFolder(@Nullable Folder folder) {
        var query = db.selectFrom(QBookmark.bookmark).where(notDeleted());
        if (folder != null) {
            query.where(QBookmark.bookmark.folder.eq(folder));
        } else {
            query.where(QBookmark.bookmark.folder.isNull());
        }
        return query.fetch();
    }

    @NonNull
    public List<Bookmark> findByFolderId(ID<Folder> folderId) {
        return db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.folder.id.eq(folderId.getUUID()).and(notDeleted()))
            .fetch();
    }

    @NonNull
    public List<Bookmark> findAllByFolderIncludingDeleted(@NonNull ID<Folder> folderId) {
        return db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.folder.id.eq(folderId.getUUID()))
            .fetch();
    }

    @NonNull
    public List<Bookmark> findByTag(@Nullable Tag tag) {
        var query = db.selectFrom(QBookmark.bookmark).where(notDeleted());
        if (tag != null) {
            query.where(QBookmark.bookmark.tags.contains(tag));
        }
        return query.fetch();
    }

    @NonNull
    public List<Bookmark> findByTagId(ID<Tag> tagId) {
        return db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.tags.any().id.eq(tagId.getUUID()).and(notDeleted()))
            .fetch();
    }

    @NonNull
    public List<Bookmark> findByCollection(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QBookmark.bookmark)
            .leftJoin(QBookmark.bookmark.tags).fetchJoin()
            .where(QBookmark.bookmark.collection.id.eq(collectionId.getUUID()).and(notDeleted()))
            .orderBy(QBookmark.bookmark.timestampErstellt.desc())
            .fetch();
    }

    @NonNull
    public List<Bookmark> findDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        if (collectionIds.isEmpty()) {
            return List.of();
        }
        var uuids = collectionIds.stream().map(ID::getUUID).toList();
        return db.selectFrom(QBookmark.bookmark)
            .leftJoin(QBookmark.bookmark.tags).fetchJoin()
            .where(QBookmark.bookmark.collection.id.in(uuids).and(deleted()))
            .orderBy(QBookmark.bookmark.deletedAt.desc())
            .fetch();
    }

    public long countDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        if (collectionIds.isEmpty()) {
            return 0L;
        }
        var uuids = collectionIds.stream().map(ID::getUUID).toList();
        Long count = db.select(QBookmark.bookmark.id.count())
            .from(QBookmark.bookmark)
            .where(QBookmark.bookmark.collection.id.in(uuids).and(deleted()))
            .fetchFirst();
        return count != null ? count : 0L;
    }

    @NonNull
    public List<Bookmark> searchByTitle(String searchTerm) {
        return db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.title.likeIgnoreCase("%" + searchTerm + "%").and(notDeleted()))
            .fetch();
    }

    @NonNull
    public List<Bookmark> findAllOldestFirstNotDeleted() {
        return db.selectFrom(QBookmark.bookmark)
            .where(notDeleted())
            .orderBy(QBookmark.bookmark.timestampErstellt.asc())
            .fetch();
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        var bookmarks = db.selectFrom(QBookmark.bookmark)
            .where(QBookmark.bookmark.collection.id.eq(collectionId.getUUID()))
            .fetch();
        for (var bookmark : bookmarks) {
            remove(bookmark.getId());
        }
    }

    @NonNull
    public List<Bookmark> findStaleByCollection(
        @NonNull ID<Collection> collectionId,
        int thresholdMonths
    ) {
        var now = OffsetDateTime.now();
        var cutoff = now.minusMonths(thresholdMonths);
        DateTimeExpression<OffsetDateTime> cutoffExpr = Expressions.dateTimeTemplate(
            OffsetDateTime.class, "({0})", cutoff
        );

        BooleanExpression inactive = QBookmark.bookmark.lastClickedAt.lt(cutoffExpr)
            .or(QBookmark.bookmark.lastClickedAt.isNull()
                .and(QBookmark.bookmark.timestampErstellt.lt(cutoffExpr)));

        BooleanExpression notDismissed = QBookmark.bookmark.suggestionDismissedAt.isNull()
            .or(QBookmark.bookmark.suggestionDismissedAt.lt(cutoffExpr));

        return db.selectFrom(QBookmark.bookmark)
            .leftJoin(QBookmark.bookmark.folder).fetchJoin()
            .where(
                QBookmark.bookmark.collection.id.eq(collectionId.getUUID()),
                notDeleted(),
                inactive,
                notDismissed
            )
            .orderBy(
                QBookmark.bookmark.lastClickedAt.asc().nullsFirst(),
                QBookmark.bookmark.timestampErstellt.asc()
            )
            .fetch();
    }
}
