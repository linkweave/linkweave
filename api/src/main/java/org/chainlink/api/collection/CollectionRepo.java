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
public class CollectionRepo extends BaseRepo<Collection> {


    @NonNull
    public Optional<Collection> findDefaultByUser(@NonNull ID<User> userId) {
        return db.selectFrom(QCollection.collection)
            .leftJoin(QCollectionAccess.collectionAccess)
            .on(QCollection.collection.id.eq(QCollectionAccess.collectionAccess.collection.id))
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .where(QCollectionAccess.collectionAccess.isDefault.isTrue())
            .fetchOne();
    }

    @NonNull
    public List<Collection> findByOwner(@NonNull ID<User> ownerId) {
        return db.selectFrom(QCollection.collection)
            .where(QCollection.collection.owner.id.eq(ownerId.getUUID()))
            .fetch();
    }

    public long countAll() {
        return db.select(QCollection.collection.id.count())
            .from(QCollection.collection)
            .fetchOne()
            .orElse(0L);
    }
}
