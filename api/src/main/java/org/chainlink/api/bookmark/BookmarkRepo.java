package org.chainlink.api.bookmark;

import java.net.URL;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.QCollection;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Repository
@RequiredArgsConstructor
public class BookmarkRepo extends BaseRepo<Bookmark> {

    private final AppClock appClock;

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

    /**
     * Bulk lookup for batch operations. Tags are fetch-joined because the
     * batch endpoints map every loaded bookmark to JSON (avoids N+1 lazy
     * loads). Unknown ids are simply absent from the result — completeness
     * is the caller's concern.
     */
    @NonNull
    public List<Bookmark> findByIdsWithTags(@NonNull List<ID<Bookmark>> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var uuids = ids.stream().map(ID::getUUID).toList();
        return db.selectFrom(QBookmark.bookmark)
            .leftJoin(QBookmark.bookmark.tags).fetchJoin()
            .where(QBookmark.bookmark.id.in(uuids))
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

    /** A bookmark awaiting screenshot capture, with the bits the capture job needs
     * to decide whether to fetch: its id, URL, and the owning collection's browser
     * fetch allowlist (hosts the backend must not reach — see {@link PendingScreenshotCapture}). */
    public record PendingScreenshotCapture(
        @NonNull ID<Bookmark> bookmarkId,
        @NonNull URL url,
        @Nullable String collectionBrowserAllowlist
    ) {}

    /**
     * Bookmarks in screenshot-enabled collections that need a (re)capture,
     * newest-modified first. A row is pending when it has never been captured
     * <em>or</em> its last capture is older than {@code recaptureAfter} — so a
     * screenshot whose cached image has expired is regenerated instead of
     * silently vanishing. Freshly-captured rows are excluded by the DB filter,
     * keeping the steady-state result set small; {@code offset} lets the job
     * page past rows blocked by a negative cache entry without re-scanning them.
     *
     * <p>Projects scalars only (no entity load, so no timestamp extraction via
     * Hibernate's shared UTC calendar — see {@link #findUrlById}) and carries
     * the collection's favicon allowlist so the job can skip hosts the backend
     * cannot reach without burning capture budget or writing negative entries.
     */
    @NonNull
    public List<PendingScreenshotCapture> findPendingScreenshotCaptures(int limit, int offset, @NonNull Duration recaptureAfter) {
        var b = QBookmark.bookmark;
        var recaptureCutoff = appClock.offsetDateTime().now().minus(recaptureAfter);
        DateTimeExpression<OffsetDateTime> cutoffExpr = Expressions.dateTimeTemplate(
            OffsetDateTime.class, "({0})", recaptureCutoff);
        BooleanExpression needsCapture = b.screenshotCapturedAt.isNull()
            .or(b.screenshotCapturedAt.lt(cutoffExpr));
        return db.select(b.id, b.url, b.collection.faviconAllowlist)
            .from(b)
            .where(notDeleted()
                .and(b.collection.screenshotEnabled.isTrue())
                .and(needsCapture))
            .orderBy(b.timestampMutiert.desc().nullsLast())
            .orderBy(b.timestampErstellt.desc())
            .offset(offset)
            .limit(limit)
            .fetch()
            .stream()
            .map(t -> new PendingScreenshotCapture(
                ID.of(Objects.requireNonNull(t.get(b.id)), Bookmark.class),
                Objects.requireNonNull(t.get(b.url)),
                t.get(b.collection.faviconAllowlist)))
            .toList();
    }

    /**
     * Projects just the URL of one bookmark. The screenshot read endpoint is
     * hit concurrently — one request per visible thumbnail/preview — and only
     * needs the URL. Loading the full entity would extract its {@code
     * OffsetDateTime} columns through Hibernate's shared static UTC
     * {@link java.util.Calendar} (HHH-20355, fixed in 7.4), which is not
     * thread-safe and races under that concurrency, throwing
     * {@code ArrayIndexOutOfBoundsException} from deep in the JDBC driver. A
     * scalar projection avoids timestamp extraction — and the entity load —
     * entirely.
     */
    @NonNull
    public Optional<URL> findUrlById(@NonNull ID<Bookmark> bookmarkId) {
        return db.select(QBookmark.bookmark.url)
            .from(QBookmark.bookmark)
            .where(QBookmark.bookmark.id.eq(bookmarkId.getUUID()))
            .fetchOne();
    }

    /** A bookmark's URL paired with its collection's browser fetch allowlist. */
    public record FaviconContext(@NonNull URL url, @Nullable String collectionBrowserAllowlist) {}

    /**
     * Scalar projection for the favicon read path: the URL plus the owning
     * collection's allowlist, so the service can skip server-side fetches for
     * hosts the browser loads directly. Like {@link #findUrlById} this avoids an
     * entity load (and its timestamp extraction). Left join so an uncollected
     * bookmark still yields its URL with a {@code null} allowlist.
     */
    @NonNull
    public Optional<FaviconContext> findFaviconContextById(@NonNull ID<Bookmark> bookmarkId) {
        var b = QBookmark.bookmark;
        var c = QCollection.collection;
        return db.select(b.url, c.faviconAllowlist)
            .from(b)
            .leftJoin(b.collection, c)
            .where(b.id.eq(bookmarkId.getUUID()))
            .fetchOne()
            .map(t -> new FaviconContext(
                Objects.requireNonNull(t.get(b.url)),
                t.get(c.faviconAllowlist)));
    }

    @NonNull
    public Optional<ID<Collection>> findCollectionIdById(@NonNull ID<Bookmark> bookmarkId) {
        return db.select(QBookmark.bookmark.collection.id)
            .from(QBookmark.bookmark)
            .where(QBookmark.bookmark.id.eq(bookmarkId.getUUID()))
            .fetchOne()
            .map(uuid -> ID.of(uuid, Collection.class));
    }

    @NonNull
    public List<Bookmark> findAllOldestFirstNotDeleted() {
        return db.selectFrom(QBookmark.bookmark)
            .where(notDeleted())
            .orderBy(QBookmark.bookmark.lastClickedAt.asc().nullsLast())
            .orderBy(QBookmark.bookmark.timestampErstellt.asc())
            .fetch();
    }

    @NonNull
    public Map<UUID, Long> countByCollectionGrouped() {
        var collectionId = QBookmark.bookmark.collection.id;
        var bookmarkCount = QBookmark.bookmark.id.count();
        var rowTuples = db.select(collectionId, bookmarkCount)
            .from(QBookmark.bookmark)
            .where(notDeleted())
            .groupBy(collectionId)
            .fetch();

        return rowTuples.stream()
            .filter(row -> row.get(collectionId) != null)  // exclude uncollected bookmarks
            .collect(Collectors.toMap(
                row -> Objects.requireNonNull(row.get(collectionId)),
                row -> Objects.requireNonNull(row.get(bookmarkCount))
            ));
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
        var now = appClock.offsetDateTime().now();
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
