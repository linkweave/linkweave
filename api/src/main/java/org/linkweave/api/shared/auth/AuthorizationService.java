package org.linkweave.api.shared.auth;

import java.util.Optional;

import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.linkweave.api.auth.apikey.ApiKey;
import org.linkweave.api.auth.apikey.ApiKeyRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionAccessRepo;
import org.linkweave.api.collection.CollectionRole;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.errorhandling.AppAuthorizationException;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final CollectionAccessRepo collectionAccessRepo;
    private final CurrentUserService currentUserService;
    private final ApiKeyRepo apiKeyRepo;

    public void requireCollectionAccess(@NonNull ID<Collection> collectionId) {
        var currentUserId = currentUserService.currentUserID();
        if (!collectionAccessRepo.hasAccess(currentUserId, collectionId)) {
            throw new AppAuthorizationException(
                I18nMessage.of("AppAuthorization.NO_COLLECTION_ACCESS", "collectionId", collectionId.getUUID().toString())
            );
        }
    }

    public void requireAccessTo(@NonNull BelongsToCollection entity) {
        requireCollectionAccess(entity.getCollectionId());
    }

    /**
     * Batch variant of {@link #requireSameCollection(BelongsToCollection, ID)}:
     * every submitted entity must belong to the already-authorized collection,
     * otherwise access to one collection would allow mutating entities of any
     * other (IDOR). Batch endpoints must call this instead of hand-rolling the
     * loop so the guard cannot be forgotten.
     */
    public void requireSameCollection(
        @NonNull Iterable<? extends BelongsToCollection> entities,
        @NonNull ID<Collection> expectedCollectionId
    ) {
        for (BelongsToCollection entity : entities) {
            requireSameCollection(entity, expectedCollectionId);
        }
    }

    /**
     * Rejects attempts to associate an entity with a collection other than its own.
     * Prevents privilege escalation when a user has access to both the source and
     * target collections (e.g. updating a saved search and silently re-homing it
     * to another collection to overwrite a name there).
     */
    public void requireSameCollection(
        @NonNull BelongsToCollection entity,
        @NonNull ID<Collection> expectedCollectionId
    ) {
        requireSameCollection(entity.getCollectionId(), expectedCollectionId);
    }

    public void requireSameCollection(
        @NonNull ID<Collection> actualCollectionId,
        @NonNull ID<Collection> expectedCollectionId
    ) {
        if (!actualCollectionId.equals(expectedCollectionId)) {
            throw new AppAuthorizationException(
                I18nMessage.of(
                    "AppAuthorization.COLLECTION_MISMATCH",
                    "expected", expectedCollectionId.getUUID().toString(),
                    "actual", actualCollectionId.getUUID().toString()
                )
            );
        }
    }

    /**
     * Uses a single query (id + user_id) so not-found and not-owned produce identical
     * timing and the same 400 response — prevents IDOR via timing side-channel.
     */
    public void requireApiKeyOwnership(@NonNull ID<ApiKey> apiKeyId) {
        var currentUserId = currentUserService.currentUserID();
        if (apiKeyRepo.findByIdAndUser(apiKeyId, currentUserId).isEmpty()) {
            throw new AppValidationException(AppValidationMessage.genericMessage("AppValidation.API_KEY_NOT_FOUND"));
        }
    }

    public boolean hasCollectionAccess(@NonNull ID<Collection> collectionId) {
        var currentUserId = currentUserService.currentUserID();
        return collectionAccessRepo.hasAccess(currentUserId, collectionId);
    }

    public void requireOwnerAccess(@NonNull ID<Collection> collectionId) {
        var currentUserId = currentUserService.currentUserID();
        var role = collectionAccessRepo.findRole(currentUserId, collectionId);
        if (role.isEmpty() || role.get() != CollectionRole.OWNER) {
            throw new AppAuthorizationException(
                I18nMessage.of("AppAuthorization.NO_OWNER_ACCESS", "collectionId", collectionId.getUUID().toString())
            );
        }
    }

    /**
     * Requires the current user to hold at least the {@link CollectionRole#ADMIN}
     * role on the collection. Used for member management and configuration changes
     * that admins may perform (everything except delete/rename, which stay owner-only).
     *
     * @return the caller's resolved role, so callers that also need it for a
     * finer-grained follow-up check don't have to re-query.
     */
    @NonNull
    public CollectionRole requireAdminOrOwner(@NonNull ID<Collection> collectionId) {
        var role = currentRole(collectionId);
        if (role.isEmpty() || !role.get().isAtLeast(CollectionRole.ADMIN)) {
            throw new AppAuthorizationException(
                I18nMessage.of("AppAuthorization.NO_ADMIN_ACCESS", "collectionId", collectionId.getUUID().toString())
            );
        }
        return role.get();
    }

    /**
     * @return the current user's role on the collection, or empty if they have no access.
     */
    @NonNull
    public Optional<CollectionRole> currentRole(@NonNull ID<Collection> collectionId) {
        var currentUserId = currentUserService.currentUserID();
        return collectionAccessRepo.findRole(currentUserId, collectionId);
    }

    /**
     * @return the given user's role on the collection, or empty if they have no access.
     * Lets the resource layer make target-dependent authorization decisions (e.g. only
     * an owner may remove another admin) without the service performing the check itself.
     */
    @NonNull
    public Optional<CollectionRole> roleOf(@NonNull ID<Collection> collectionId, @NonNull ID<User> userId) {
        return collectionAccessRepo.findRole(userId, collectionId);
    }
}
