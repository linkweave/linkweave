package org.chainlink.api.collection;

import java.util.List;
import java.util.Optional;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.AllArgsConstructor;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.BaseRepo;
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
    public Optional<CollectionAccess> findDefaultByUser(@NonNull ID<User> userId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .where(QCollectionAccess.collectionAccess.isDefault.isTrue())
            .fetchOne();
    }

    public boolean existsByUser(@NonNull ID<User> userId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .fetchFirst() != null;
    }
}
