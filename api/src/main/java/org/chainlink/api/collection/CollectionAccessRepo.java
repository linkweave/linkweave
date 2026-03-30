package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.AllArgsConstructor;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
@AllArgsConstructor
public class CollectionAccessRepo extends BaseRepo<CollectionAccess> {



    @NonNull
    public List<CollectionAccess> findByUser(@NonNull ID<User> userId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .fetch();
    }

    @NonNull
    public CollectionAccess getDefaultByUser(@NonNull ID<User> userId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .where(QCollectionAccess.collectionAccess.isDefault.isTrue())
            .fetchOne().orElseThrow(() -> new AppFailureException(AppFailureMessage.entityNotFoundMsg( CollectionAccess.class, "no default collection for user")));
    }

    public boolean existsByUser(@NonNull ID<User> userId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .fetchFirst() != null;
    }

    public boolean hasAccess(@NonNull ID<User> userId, @NonNull ID<Collection> collectionId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .where(QCollectionAccess.collectionAccess.collection.id.eq(collectionId.getUUID()))
            .fetchFirst() != null;
    }
}
