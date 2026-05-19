package org.chainlink.api.auth;

import java.util.List;
import java.util.Set;

import ch.dvbern.dvbstarter.types.id.ID;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.bookmark.TagService;
import org.chainlink.api.bookmark.folder.FolderService;
import org.chainlink.api.bookmark.property.PropertyDefinitionService;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccess;
import org.chainlink.api.collection.CollectionAccessRepo;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.api.collection.CollectionService;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.errorhandling.AppAuthException;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepo userRepo;
    private final CollectionRepo collectionRepo;
    private final CollectionAccessRepo collectionAccessRepo;
    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final TagService tagService;
    private final PropertyDefinitionService propertyDefinitionService;
    private final CollectionService collectionService;
    private final CurrentUserService currentUserService;
    private final UserSettingsService userSettingsService;

    public @NonNull UserInfoJson buildCurrentUserInfo(@NonNull String email, @NonNull Set<String> roles) {
        User user = currentUserService.findCurrentUser().orElseThrow(AppAuthException::new);
        Collection collection = collectionService.getDefaultCollectionOrAutoprovision(user);
        UserSettingsJson settings = userSettingsService.getSettingsForUser(user);
        return new UserInfoJson(
            email,
            user.getVorname(),
            user.getNachname(),
            roles,
            collection.getId(),
            settings
        );
    }

    /**
     * Hard-deletes the given user and everything they own — collections,
     * bookmarks, tags, folders, auto-tag rules, and all CollectionAccess rows
     * (both grants on the user's own collections and the user's memberships of
     * other people's collections). UserPermission rows cascade automatically
     * via {@code User.berechtigungen}.
     *
     * Bypasses the "can't delete last collection" guard used by the regular
     * collection-delete path — when the user is going away there is no last
     * collection to preserve.
     */
    @Transactional
    public void hardDeleteUser(@NonNull ID<User> userId) {
        LOG.info("Hard-deleting user with ID: {}", userId);

        // 1. Drop access rows where the user is a member of someone else's
        // collection. Done first so step 2's per-collection access cleanup
        // doesn't have to also worry about these.
        List<CollectionAccess> memberships = collectionAccessRepo.findByUser(userId);
        for (CollectionAccess access : memberships) {
            collectionAccessRepo.remove(access.getId());
        }

        // 2. Cascade-delete every collection the user owns. Inlined from
        // CollectionService.deleteCollection() to skip the last-collection
        // guard which doesn't apply when the owner itself is being deleted.
        List<Collection> ownedCollections = collectionRepo.findByOwner(userId);
        for (Collection collection : ownedCollections) {
            ID<Collection> collectionId = collection.getId();
            bookmarkService.deleteByCollection(collectionId);
            folderService.deleteByCollection(collectionId);
            tagService.deleteByCollection(collectionId);
            propertyDefinitionService.deleteByCollection(collectionId);

            for (CollectionAccess access : collectionAccessRepo.findByCollection(collectionId)) {
                collectionAccessRepo.remove(access.getId());
            }

            collectionRepo.remove(collectionId);
        }

        // Remove settings (may not exist for legacy users — bulk delete is a no-op if absent).
        userSettingsService.deleteSettingsForUser(userId);

        // Finally remove the user. UserPermission cascades via the
        // User.berechtigungen @OneToMany(cascade = ALL, orphanRemoval = true).
        userRepo.remove(userId);
    }
}
