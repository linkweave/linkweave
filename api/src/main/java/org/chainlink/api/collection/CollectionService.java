package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.TagService;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderService;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class CollectionService {

    private static final String DEFAULT_COLLECTION_NAME = "My Bookmarks";

    private final CollectionRepo collectionRepo;
    private final CollectionAccessRepo collectionAccessRepo;
    private  final FolderService folderService;
    private final TagService tagService;

    private final CollectionInfoMapperService collectionInfoMapperService;
    private final BookmarkService bookmarkService;

    public Collection getDefaultCollectionOrAutoprovision(@NonNull User user) {

        var optCollection = collectionRepo.findDefaultByUser(user.getId());
        if (optCollection.isPresent()) {
            return optCollection.get();
        }

        Collection collection = new Collection(DEFAULT_COLLECTION_NAME, user);
        collectionRepo.persistAndFlush(collection);

        if (!collectionAccessRepo.existsByUser(user.getId())) {
            CollectionAccess access = new CollectionAccess(
                collection,
                user,
                CollectionRole.OWNER,
                true
            );
            collectionAccessRepo.persistAndFlush(access);
        }
        return collection;
    }

    public void saveCollection(@NonNull Collection collection) {
        collectionRepo.persist(collection);
    }

    public List<CollectionSummaryJson> findCollectionsForUser(@NonNull User user) {
        return collectionAccessRepo.findByUser(user.getId()).stream()
            .map(access -> new CollectionSummaryJson(
                access.getCollection().getId(),
                access.getCollection().getName(),
                access.isDefault(),
                access.getRole()
            ))
            .toList();
    }

    public void setDefaultCollection(@NonNull ID<Collection> collectionId, @NonNull User user) {
        collectionAccessRepo.setDefaultForUser(user.getId(), collectionId);
    }

    public CollectionSummaryJson createCollection(@NonNull CollectionCreateJson json, @NonNull User user) {
        Collection collection = new Collection(json.getName(), user);
        collectionRepo.persist(collection);

        CollectionAccess access = new CollectionAccess(
            collection,
            user,
            CollectionRole.OWNER,
            false
        );
        collectionAccessRepo.persistAndFlush(access);

        return new CollectionSummaryJson(
            collection.getId(),
            collection.getName(),
            access.isDefault(),
            access.getRole()
        );
    }

    public void updateCollection(@NonNull ID<Collection> id, @NonNull CollectionUpdateJson json) {
        Collection collection = collectionRepo.getById(id);
        collection.setName(json.getName());
    }

    public void deleteCollection(@NonNull ID<Collection> id) {
        // To keep it simple, we just delete the collection.
        // The foreign keys in the database will prevent deletion if there are still dependencies.
        // But for this "fix" it should be enough to have the endpoint.
        collectionRepo.remove(id);
    }

    public CollectionInfoJson getCollectionInfoById(@NonNull ID<Collection> collectionID) {

        List<Bookmark> bookmarks = bookmarkService.getBookmarksByCollection(collectionID);
        Collection collection = collectionRepo.getById(collectionID);
        List<Folder> folders = folderService.getFoldersByCollection(collectionID);
        List<Tag> tags = tagService.findByCollection(collectionID);
        return collectionInfoMapperService.toCollectionInfoJson(
            collection,
            bookmarks,
            folders,
            tags
        );
    }
}
