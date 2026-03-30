package org.chainlink.api.collection;

import java.util.List;

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
    public List<Collection> findByOwner(@NonNull ID<User> ownerId) {
        return db.selectFrom(QCollection.collection)
            .where(QCollection.collection.owner.id.eq(ownerId.getUUID()))
            .fetch();
    }
}
