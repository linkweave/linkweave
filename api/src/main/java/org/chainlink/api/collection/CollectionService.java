package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.bookmark.Tag;
import org.chainlink.api.bookmark.TagService;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderService;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionService {

    private static final String DEFAULT_COLLECTION_NAME = "My Bookmarks";

    private final CollectionRepo collectionRepo;
    private final CollectionAccessRepo collectionAccessRepo;
    private final FolderService folderService;
    private final TagService tagService;
    private final CollectionInfoMapperService collectionInfoMapperService;
    private final BookmarkService bookmarkService;
    private final UserRepo userRepo;

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
        return collectionAccessRepo.findCollectionSummariesForUser(user.getId());
    }

    public void setDefaultCollection(@NonNull ID<Collection> collectionId, @NonNull User user) {
        collectionAccessRepo.setDefaultForUser(user.getId(), collectionId);
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

    @NonNull
    public CollectionSummaryJson createCollection(@NonNull String name, @NonNull User owner) {
        Collection collection = new Collection(name, owner);
        collectionRepo.persistAndFlush(collection);

        CollectionAccess access = new CollectionAccess(collection, owner, CollectionRole.OWNER, false);
        collectionAccessRepo.persistAndFlush(access);

        return new CollectionSummaryJson(
            collection.getId(),
            collection.getName(),
            false,
            CollectionRole.OWNER,
            false
        );
    }

    @NonNull
    public CollectionSummaryJson updateCollection(@NonNull ID<Collection> collectionId, @NonNull String name, @NonNull User user) {
        Collection collection = collectionRepo.getById(collectionId);
        collection.setName(name);
        collectionRepo.persistAndFlush(collection);

        CollectionAccess access = collectionAccessRepo.findByUser(user.getId()).stream()
            .filter(a -> a.getCollection().getId().equals(collectionId))
            .findFirst()
            .orElseThrow();

        boolean shared = collectionAccessRepo.findByCollection(collectionId).size() > 1;

        return new CollectionSummaryJson(
            collection.getId(),
            collection.getName(),
            access.isDefault(),
            access.getRole(),
            shared
        );
    }

    public void deleteCollection(@NonNull ID<Collection> collectionId, @NonNull User user) {
        LOG.info("Deleting collection with ID: {}", collectionId);
        var userAccesses = collectionAccessRepo.findByUser(user.getId());
        boolean wasDefault = userAccesses.stream()
            .anyMatch(a -> a.getCollection().getId().equals(collectionId) && a.isDefault());
        var remainingCollectionIds = userAccesses.stream()
            .filter(a -> !a.getCollection().getId().equals(collectionId))
            .sorted(java.util.Comparator.comparing(a -> a.getCollection().getTimestampErstellt()))
            .map(a -> a.getCollection().getId())
            .toList();

        if (wasDefault && remainingCollectionIds.isEmpty()) {
            throw new AppValidationException(AppValidationMessage.cantDeleteLastCollection());
        }

        bookmarkService.deleteByCollection(collectionId);
        folderService.deleteByCollection(collectionId);
        tagService.deleteByCollection(collectionId);

        var allAccessesForCollection = collectionAccessRepo.findByCollection(collectionId);
        for (var access : allAccessesForCollection) {
            collectionAccessRepo.remove(access.getId());
        }

        collectionRepo.remove(collectionId);

        if (wasDefault) {
            collectionAccessRepo.setDefaultForUser(user.getId(), remainingCollectionIds.getFirst());
        }
    }

    @NonNull
    public List<CollectionMemberJson> listMembers(@NonNull ID<Collection> collectionId) {
        return collectionAccessRepo.findByCollection(collectionId).stream()
            .map(access -> new CollectionMemberJson(
                access.getUser().getId(),
                access.getUser().getEmail().getAddress(),
                access.getUser().getVornameName(),
                access.getRole()
            ))
            .toList();
    }

    @NonNull
    public CollectionMemberJson shareWithUser(
        @NonNull ID<Collection> collectionId,
        @NonNull EmailAddress targetEmail,
        @NonNull User currentUser
    ) {

        if (targetEmail.equals(currentUser.getEmail())) {
            throw new AppValidationException(AppValidationMessage.shareCannotShareWithSelf());
        }

        var targetUser = userRepo.findByEmail(targetEmail)
            .orElseThrow(() -> new AppValidationException(AppValidationMessage.shareUserNotFound(targetEmail.toString())));

        boolean alreadyHasAccess = collectionAccessRepo.hasAccess(targetUser.getId(), collectionId);
        if (alreadyHasAccess) {
            throw new AppValidationException(AppValidationMessage.shareAlreadyHasAccess());
        }

        CollectionAccess access = new CollectionAccess(
            collectionRepo.getById(collectionId),
            targetUser,
            CollectionRole.MEMBER,
            false
        );
        collectionAccessRepo.persistAndFlush(access);

        return new CollectionMemberJson(
            targetUser.getId(),
            targetUser.getEmail().getAddress(),
            targetUser.getVornameName(),
            CollectionRole.MEMBER
        );
    }

    public void revokeAccess(@NonNull ID<Collection> collectionId, @NonNull ID<User> targetUserId) {
        collectionAccessRepo.findByCollection(collectionId).stream()
            .filter(a -> a.getUser().getId().equals(targetUserId))
            .forEach(a -> collectionAccessRepo.remove(a.getId()));
    }
}
