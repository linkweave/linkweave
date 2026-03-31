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
    public Bookmark createBookmark(@NonNull BookmarkSaveJson json) {
        ID<Collection> collectionId = json.getCollectionId();
        ID<Folder> folderId = json.getFolderId();

        if (folderId != null) {
            Folder folder = folderRepo.getById(folderId);
            if (!folder.collection.getId().equals(collectionId)) {
                throw new AppFailureException(
                    AppFailureMessage.internalError("Folder does not belong to the specified collection")
                );
            }
        }

        Set<Tag> tags = new HashSet<>();
        Set<ID<Tag>> tagIds = json.getTagIds();
        if (tagIds != null) {
            for (ID<Tag> tagId : tagIds) {
                tags.add(tagRepo.getById(tagId));
            }
        }

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
