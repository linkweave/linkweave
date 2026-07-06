package org.linkweave.api.collection;

import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.bookmark.AutoTagRuleService;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.bookmark.SavedSearchService;
import org.linkweave.api.bookmark.TagService;
import org.linkweave.api.bookmark.folder.FolderService;
import org.linkweave.api.bookmark.property.PropertyDefinitionService;
import org.linkweave.api.collection.favicon.BrowserFetchAllowlist;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.emailaddress.EmailAddress;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionService {

    private static final String DEFAULT_COLLECTION_NAME = "My Bookmarks";

    private final CollectionRepo collectionRepo;
    private final CollectionAccessRepo collectionAccessRepo;
    private final FolderService folderService;
    private final TagService tagService;
    private final AutoTagRuleService autoTagRuleService;
    private final BookmarkService bookmarkService;
    private final PropertyDefinitionService propertyDefinitionService;
    private final SavedSearchService savedSearchService;
    private final UserRepo userRepo;
    private final CurrentUserService currentUserService;

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

    public List<ID<Collection>> findCollectionIdsForCurrentUser() {
        return collectionAccessRepo.findByUser(currentUserService.currentUserID()).stream()
            .map(CollectionAccess::getCollection)
            .map(Collection::getId)
            .toList();
    }

    public void setDefaultCollection(@NonNull ID<Collection> collectionId, @NonNull User user) {
        collectionAccessRepo.setDefaultForUser(user.getId(), collectionId);
    }

    public Collection getCollection(@NonNull ID<Collection> collectionId) {
        return collectionRepo.getById(collectionId);
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
    public CollectionSummaryJson updateCollection(
        @NonNull ID<Collection> collectionId,
        @NonNull String name,
        @Nullable String browserFetchAllowlist,
        boolean screenshotEnabled,
        @NonNull User user,
        @Nullable CollectionRole callerRole
    ) {
        Collection collection = collectionRepo.getById(collectionId);
        // Renaming a collection is owner-only; admins keep the existing name.
        if (callerRole == CollectionRole.OWNER) {
            collection.setName(name);
        }
        BrowserFetchAllowlist parsed = BrowserFetchAllowlist.parse(browserFetchAllowlist);
        collection.setBrowserFetchAllowlist(parsed.patterns().isEmpty() ? null : String.join("\n", parsed.patterns()));
        collection.setScreenshotEnabled(screenshotEnabled);
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
        autoTagRuleService.deleteByCollection(collectionId);
        propertyDefinitionService.deleteByCollection(collectionId);
        savedSearchService.deleteByCollection(collectionId);

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
        @Nullable CollectionRole requestedRole,
        @NonNull User currentUser
    ) {

        if (targetEmail.equals(currentUser.getEmail())) {
            throw new AppValidationException(AppValidationMessage.shareCannotShareWithSelf());
        }

        CollectionRole effectiveRole = requestedRole == null ? CollectionRole.MEMBER : requestedRole;
        // A collection has exactly one (immutable) owner — OWNER can never be granted here.
        // Who may grant ADMIN is enforced in the resource layer.
        if (effectiveRole == CollectionRole.OWNER) {
            throw new AppValidationException(AppValidationMessage.shareCannotAssignOwner());
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
            effectiveRole,
            false
        );
        collectionAccessRepo.persistAndFlush(access);

        return new CollectionMemberJson(
            targetUser.getId(),
            targetUser.getEmail().getAddress(),
            targetUser.getVornameName(),
            effectiveRole
        );
    }

    public void revokeAccess(@NonNull ID<Collection> collectionId, @NonNull ID<User> targetUserId) {
        CollectionAccess accessToRevoke = collectionAccessRepo
            .findByUserAndCollectionOptional(targetUserId, collectionId)
            .orElseThrow(() -> new AppValidationException(AppValidationMessage.collectionMemberNotFound()));

        // The owner can never be removed — a collection must always remain manageable.
        // Whether an admin may remove another admin is enforced in the resource layer.
        if (accessToRevoke.getRole() == CollectionRole.OWNER) {
            throw new AppValidationException(AppValidationMessage.cannotRevokeOwner());
        }

        if (accessToRevoke.isDefault()) {
            var userAccesses = collectionAccessRepo.findByUser(targetUserId);
            userAccesses.stream()
                .filter(a -> !a.getCollection().getId().equals(collectionId))
                .min(Comparator.comparing(a -> a.getCollection().getTimestampErstellt()))
                .ifPresent(next -> collectionAccessRepo.setDefaultForUser(targetUserId, next.getCollection().getId()));
        }
        collectionAccessRepo.remove(accessToRevoke.getId());
    }

    /**
     * Changes an existing member's role between {@code MEMBER} and {@code ADMIN}.
     * The OWNER's role is immutable and {@code OWNER} cannot be assigned here.
     * Who may trigger a given transition (owner-only, except an admin stepping
     * themselves down) is enforced in the resource layer.
     */
    @NonNull
    public CollectionMemberJson changeMemberRole(
        @NonNull ID<Collection> collectionId,
        @NonNull ID<User> targetUserId,
        @NonNull CollectionRole newRole
    ) {
        if (newRole == CollectionRole.OWNER) {
            throw new AppValidationException(AppValidationMessage.cannotChangeOwnerRole());
        }

        CollectionAccess target = collectionAccessRepo
            .findByUserAndCollectionOptional(targetUserId, collectionId)
            .orElseThrow(() -> new AppValidationException(AppValidationMessage.collectionMemberNotFound()));
        if (target.getRole() == CollectionRole.OWNER) {
            throw new AppValidationException(AppValidationMessage.cannotChangeOwnerRole());
        }

        // target is already managed; the UPDATE is flushed by the surrounding transaction.
        target.setRole(newRole);

        return new CollectionMemberJson(
            target.getUser().getId(),
            target.getUser().getEmail().getAddress(),
            target.getUser().getVornameName(),
            newRole
        );
    }
}

