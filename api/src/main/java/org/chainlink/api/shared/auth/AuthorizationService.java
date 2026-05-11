package org.chainlink.api.shared.auth;

import ch.dvbern.dvbstarter.types.id.ID;
import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccessRepo;
import org.chainlink.api.collection.CollectionRole;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.infrastructure.errorhandling.AppAuthorizationException;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final CollectionAccessRepo collectionAccessRepo;
    private final CurrentUserService currentUserService;

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
}
