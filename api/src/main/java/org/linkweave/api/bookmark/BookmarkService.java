package org.linkweave.api.bookmark;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.bookmark.json.BookmarkSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import org.linkweave.infrastructure.errorhandling.AppAuthorizationException;
import org.linkweave.infrastructure.errorhandling.AppFailureException;
import org.linkweave.infrastructure.errorhandling.AppFailureMessage;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.linkweave.api.shared.Util;
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final FolderRepo folderRepo;
    private final TagRepo tagRepo;
    private final AppClock appClock;

    /**
     * Returned shape for {@link #findFaviconEvictionCandidatesOldestFirst()}. Keeps the entity
     * graph from leaking into background-job callers.
     */
    public record FaviconEvictionCandidate(@NonNull URL url, @Nullable OffsetDateTime createdAt) {}

    @NonNull
    public List<FaviconEvictionCandidate> findFaviconEvictionCandidatesOldestFirst() {
        return bookmarkRepo.findAllOldestFirstNotDeleted().stream()
            .map(b -> new FaviconEvictionCandidate(b.getUrl(), Util.coalesce(b.getLastClickedAt(), b.getTimestampErstellt()).orElse(null)))
            .toList();
    }

    @NonNull
    public List<Bookmark> getBookmarksByCollection(@NonNull ID<Collection> collectionId) {
        return bookmarkRepo.findByCollection(collectionId);
    }

    @NonNull
    public Bookmark getBookmark(@NonNull ID<Bookmark> id) {
        return bookmarkRepo.getById(id);
    }

    /**
     * Owning collection id without loading the entity — for the per-bookmark
     * read endpoints (favicon/screenshot) whose fan-out makes the entity
     * load's timestamp extraction race (HHH-20355, see
     * {@link BookmarkRepo#findCollectionIdById}).
     */
    @NonNull
    public ID<Collection> getBookmarkCollectionId(@NonNull ID<Bookmark> bookmarkId) {
        return bookmarkRepo.findCollectionIdById(bookmarkId)
            .orElseGet(() -> bookmarkRepo.getById(bookmarkId).getCollectionId()); // throws the canonical not-found
    }

    @NonNull
    public Bookmark createBookmark(@NonNull BookmarkSaveJson json) {
        ID<Collection> collectionId = json.getCollectionId();
        ID<Folder> folderId = json.getFolderId();

        if (folderId != null) {
            requireFolderBelongsToCollection(folderRepo.getById(folderId), collectionId);
        }

        Set<Tag> tags = resolveTags(json.getTagIds());

        Bookmark bookmark = new Bookmark(
            collectionRepo.referenceById(collectionId),
            folderId != null ? folderRepo.referenceById(folderId) : null,
            json.getTitle(),
            parseUrl(json.getUrl()),
            json.getDescription(),
            tags,
            new HashSet<>(),
            0,
            null,
            null,
            null,
            null
        );

        bookmarkRepo.persist(bookmark);
        return bookmark;
    }

    @NonNull
    public Bookmark updateBookmark(@NonNull ID<Bookmark> bookmarkId, @NonNull BookmarkSaveJson json) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);

        ID<Collection> collectionId = json.getCollectionId();
        ID<Folder> folderId = json.getFolderId();

        if (folderId != null) {
            requireFolderBelongsToCollection(folderRepo.getById(folderId), collectionId);
        }

        Set<Tag> tags = resolveTags(json.getTagIds());

        bookmark.setCollection(collectionRepo.referenceById(collectionId));
        bookmark.setFolder(folderId != null ? folderRepo.referenceById(folderId) : null);
        bookmark.setTitle(json.getTitle());
        bookmark.setUrl(parseUrl(json.getUrl()));
        bookmark.setDescription(json.getDescription());
        bookmark.setTags(tags);

        bookmarkRepo.persist(bookmark);
        return bookmark;
    }

    public void removeBookmark(@NonNull ID<Bookmark> id) {
        batchRemove(List.of(bookmarkRepo.getById(id)));
    }

    @NonNull
    public List<Bookmark> getDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        return bookmarkRepo.findDeletedByCollections(collectionIds);
    }

    public long countDeletedByCollections(@NonNull List<ID<Collection>> collectionIds) {
        return bookmarkRepo.countDeletedByCollections(collectionIds);
    }

    @NonNull
    public Bookmark restoreBookmark(@NonNull ID<Bookmark> id) {
        Bookmark bookmark = bookmarkRepo.getById(id);
        if (bookmark.getDeletedAt() == null) {
            return bookmark;
        }
        Folder folder = bookmark.getFolder();
        if (folder != null && folder.getDeletedAt() != null) {
            bookmark.setFolder(null);
        }
        bookmark.setDeletedAt(null);
        bookmarkRepo.persist(bookmark);
        return bookmark;
    }

    public void purgeBookmark(@NonNull ID<Bookmark> id) {
        bookmarkRepo.remove(id);
    }

    public void emptyTrashbin(@NonNull List<ID<Collection>> collectionIds) {
        for (Bookmark bookmark : bookmarkRepo.findDeletedByCollections(collectionIds)) {
            bookmarkRepo.remove(bookmark.getId());
        }
    }

    /**
     * Bulk-loads a batch in one query (tags fetch-joined for the JSON mapping).
     * A single unknown id aborts the whole batch with the canonical not-found
     * error — callers run inside one transaction, which keeps batch operations
     * atomic.
     */
    @NonNull
    public List<Bookmark> getBookmarks(@NonNull List<ID<Bookmark>> bookmarkIds) {
        List<Bookmark> bookmarks = bookmarkRepo.findByIdsWithTags(bookmarkIds);
        Set<UUID> found = bookmarks.stream()
            .map(b -> b.getId().getUUID())
            .collect(Collectors.toSet());
        bookmarkIds.stream()
            .filter(id -> !found.contains(id.getUUID()))
            .findFirst()
            .ifPresent(bookmarkRepo::getById); // throws the canonical not-found
        return bookmarks;
    }

    public void batchMoveToFolder(
        @NonNull List<Bookmark> bookmarks,
        @Nullable ID<Folder> folderId,
        @NonNull ID<Collection> collectionId
    ) {
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepo.getById(folderId);
            requireFolderBelongsToCollection(folder, collectionId);
        }
        for (Bookmark bookmark : bookmarks) {
            bookmark.setFolder(folder);
            bookmarkRepo.persist(bookmark);
        }
    }

    public void batchRemove(@NonNull List<Bookmark> bookmarks) {
        OffsetDateTime now = appClock.offsetDateTime().now();
        for (Bookmark bookmark : bookmarks) {
            bookmark.setDeletedAt(now);
            bookmarkRepo.persist(bookmark);
        }
    }

    /**
     * Tri-state batch tag edit (UC-074a): adds every tag in {@code addTagIds} and removes
     * every tag in {@code removeTagIds} from each bookmark, in a single transaction. Either
     * list may be empty. A tag appearing in both lists is removed (remove wins). All tags are
     * validated against the collection up front so the whole edit rolls back if any is foreign.
     */
    public void batchEditTags(
        @NonNull List<Bookmark> bookmarks,
        @NonNull List<ID<Tag>> addTagIds,
        @NonNull List<ID<Tag>> removeTagIds,
        @NonNull ID<Collection> collectionId
    ) {
        // Reject contradictory requests rather than silently letting remove win:
        // a direct API caller asking to both add and remove the same tag is a bug.
        if (!Collections.disjoint(addTagIds, removeTagIds)) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.batchTag.overlappingTagIds"));
        }
        List<Tag> addTags = addTagIds.stream().map(tagRepo::getById).toList();
        List<Tag> removeTags = removeTagIds.stream().map(tagRepo::getById).toList();
        addTags.forEach(tag -> requireTagBelongsToCollection(tag, collectionId));
        removeTags.forEach(tag -> requireTagBelongsToCollection(tag, collectionId));
        for (Bookmark bookmark : bookmarks) {
            bookmark.getTags().addAll(addTags);
            removeTags.forEach(bookmark.getTags()::remove);
            bookmarkRepo.persist(bookmark);
        }
    }

    private void requireFolderBelongsToCollection(@NonNull Folder folder, @NonNull ID<Collection> collectionId) {
        if (!folder.getCollection().getId().equals(collectionId)) {
            throw new AppAuthorizationException(
                I18nMessage.of("AppAuthorization.FOLDER_COLLECTION_MISMATCH",
                    "folderId", folder.getId().getUUID().toString(),
                    "collectionId", collectionId.getUUID().toString())
            );
        }
    }

    private void requireTagBelongsToCollection(@NonNull Tag tag, @NonNull ID<Collection> collectionId) {
        if (!tag.getCollectionId().equals(collectionId)) {
            throw new AppAuthorizationException(
                I18nMessage.of("AppAuthorization.TAG_COLLECTION_MISMATCH",
                    "tagId", tag.getId().getUUID().toString(),
                    "collectionId", collectionId.getUUID().toString())
            );
        }
    }

    private Set<Tag> resolveTags(Set<ID<Tag>> tagIds) {
        Set<Tag> tags = new HashSet<>();
        if (tagIds != null) {
            for (ID<Tag> tagId : tagIds) {
                tags.add(tagRepo.getById(tagId));
            }
        }
        return tags;
    }

    private URL parseUrl(String urlString) {
        try {
            return URI.create(urlString).toURL();
        } catch (MalformedURLException _) {
            throw new AppFailureException(
                AppFailureMessage.internalError("Invalid URL format: " + urlString)
            );
        }
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        bookmarkRepo.deleteByCollection(collectionId);
    }

    public void trackClick(@NonNull ID<Bookmark> bookmarkId) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        bookmark.setClickCount(bookmark.getClickCount() + 1);
        bookmark.setLastClickedAt(appClock.offsetDateTime().now());
        bookmarkRepo.persist(bookmark);
    }
}
