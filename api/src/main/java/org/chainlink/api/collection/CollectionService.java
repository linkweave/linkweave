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
