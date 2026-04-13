package org.chainlink.api.collection;

import java.util.List;
import java.util.Optional;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
@RequiredArgsConstructor
public class CollectionAccessRepo extends BaseRepo<CollectionAccess> {

    @Override
    public void persist(@NonNull CollectionAccess entity) {

        if (entity.isDefault()) {
            db.update(QCollectionAccess.collectionAccess)
                .set(QCollectionAccess.collectionAccess.isDefault, false)
                .where(QCollectionAccess.collectionAccess.user.id.eq(entity.getUser().getId().getUUID()))
                .where(QCollectionAccess.collectionAccess.isDefault.isTrue())
                .execute();
        }
        super.persist(entity);
    }

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

    @NonNull
    public Optional<CollectionRole> findRole(@NonNull ID<User> userId, @NonNull ID<Collection> collectionId) {
        return db.select(QCollectionAccess.collectionAccess.role)
            .from(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .where(QCollectionAccess.collectionAccess.collection.id.eq(collectionId.getUUID()))
            .fetchOne();
    }

    public void setDefaultForUser(@NonNull ID<User> userId, @NonNull ID<Collection> collectionId) {
        List<CollectionAccess> userAccesses = findByUser(userId);

        for (var access : userAccesses) {
            access.setDefault(access.getCollection().getId().equals(collectionId));
        }

        db.flush();
    }

    @NonNull
    public List<CollectionAccess> findByCollection(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.collection.id.eq(collectionId.getUUID()))
            .fetch();
    }
}
