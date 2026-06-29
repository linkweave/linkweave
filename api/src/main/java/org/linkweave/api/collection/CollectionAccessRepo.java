package org.linkweave.api.collection;

import java.util.List;
import java.util.Optional;

import org.linkweave.api.types.id.ID;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.shared.user.User;
import org.linkweave.infrastructure.db.BaseRepo;
import org.linkweave.infrastructure.errorhandling.AppFailureException;
import org.linkweave.infrastructure.errorhandling.AppFailureMessage;
import org.linkweave.infrastructure.stereotypes.Repository;
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
    public CollectionAccess findByUserAndCollection(@NonNull ID<User> userId, @NonNull ID<Collection> collectionId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .where(QCollectionAccess.collectionAccess.collection.id.eq(collectionId.getUUID()))
            .fetchOne().orElseThrow(() -> new AppFailureException(AppFailureMessage.entityNotFoundMsg( CollectionAccess.class, "no collection access for user and collection")));
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
        // Two-step bulk update to avoid momentarily having two defaults (which would
        // violate the partial unique index on (user_id) WHERE isDefault = 1).
        // Note: bulk updates bypass the Hibernate L1 cache — callers must not read
        // isDefault from already-managed entities after this call within the same session.
        db.update(QCollectionAccess.collectionAccess)
            .set(QCollectionAccess.collectionAccess.isDefault, false)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .execute();

        db.update(QCollectionAccess.collectionAccess)
            .set(QCollectionAccess.collectionAccess.isDefault, true)
            .where(QCollectionAccess.collectionAccess.user.id.eq(userId.getUUID()))
            .where(QCollectionAccess.collectionAccess.collection.id.eq(collectionId.getUUID()))
            .execute();
    }


    @NonNull
    public List<CollectionAccess> findByCollection(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QCollectionAccess.collectionAccess)
            .where(QCollectionAccess.collectionAccess.collection.id.eq(collectionId.getUUID()))
            .fetch();
    }

    public long countSharedCollections() {
        var ca = QCollectionAccess.collectionAccess;
        return db.select(ca.collection.id.countDistinct())
            .from(ca)
            .where(ca.collection.id.in(
                JPAExpressions.select(ca.collection.id)
                    .from(ca)
                    .groupBy(ca.collection.id)
                    .having(ca.collection.id.count().gt(1L))
            ))
            .fetchOne()
            .orElse(0L);
    }

    @NonNull
    public List<CollectionSummaryJson> findCollectionSummariesForUser(@NonNull ID<User> userId) {
        var ca = QCollectionAccess.collectionAccess;

        return db.select(new QCollectionSummaryJson(
            ca.collection.id,
            ca.collection.name,
            ca.isDefault,
            ca.role,
            Expressions.booleanTemplate(
                "(SELECT COUNT(ca2) FROM CollectionAccess ca2 WHERE ca2.collection.id = {0}) > 1",
                ca.collection.id
            )
        ))
            .from(ca)
            .where(ca.user.id.eq(userId.getUUID()))
            .fetch();
    }
}
