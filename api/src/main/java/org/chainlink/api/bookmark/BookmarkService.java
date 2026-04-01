package org.chainlink.api.bookmark;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderRepo;
import org.chainlink.api.bookmark.json.BookmarkMoveJson;
import org.chainlink.api.bookmark.json.BookmarkSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final FolderRepo folderRepo;
    private final TagRepo tagRepo;

    @NonNull
    public List<Bookmark> getBookmarksByCollection(@NonNull ID<Collection> collectionId) {
        return bookmarkRepo.findByCollection(collectionId);
    }

    @NonNull
    public Bookmark getBookmark(@NonNull ID<Bookmark> id) {
        return bookmarkRepo.getById(id);
    }

    @NonNull
    public Bookmark createBookmark(@NonNull BookmarkSaveJson json) {
        ID<Collection> collectionId = json.getCollectionId();
        ID<Folder> folderId = json.getFolderId();

        if (folderId != null) {
            requireFolderBelongsToCollection(folderRepo.getById(folderId), collectionId);
        }

        Set<Tag> tags = resolveTags(json.getTagIds());

        Bookmark bookmark = new Bookmark();
        bookmark.collection = collectionRepo.referenceById(collectionId);
        bookmark.folder = folderId != null ? folderRepo.referenceById(folderId) : null;
        bookmark.title = json.getTitle();
        bookmark.url = parseUrl(json.getUrl());
        bookmark.description = json.getDescription();
        bookmark.tags = tags;

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

        bookmark.collection = collectionRepo.referenceById(collectionId);
        bookmark.folder = folderId != null ? folderRepo.referenceById(folderId) : null;
        bookmark.title = json.getTitle();
        bookmark.url = parseUrl(json.getUrl());
        bookmark.description = json.getDescription();
        bookmark.tags = tags;

        bookmarkRepo.persist(bookmark);
        return bookmark;
    }

    public void removeBookmark(@NonNull ID<Bookmark> id) {
        bookmarkRepo.remove(id);
    }

    @NonNull
    public Bookmark moveBookmarkToFolder(@NonNull ID<Bookmark> bookmarkId, @NonNull BookmarkMoveJson json) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        ID<Folder> folderId = json.getFolderId();

        if (folderId != null) {
            Folder folder = folderRepo.getById(folderId);
            requireFolderBelongsToCollection(folder, json.getCollectionId());
            bookmark.folder = folder;
        } else {
            bookmark.folder = null;
        }

        bookmarkRepo.persist(bookmark);
        return bookmark;
    }

    private void requireFolderBelongsToCollection(@NonNull Folder folder, @NonNull ID<Collection> collectionId) {
        if (!folder.collection.getId().equals(collectionId)) {
            throw new AppFailureException(
                AppFailureMessage.internalError("Folder does not belong to the specified collection")
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
}
