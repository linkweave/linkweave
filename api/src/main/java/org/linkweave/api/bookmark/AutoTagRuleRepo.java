package org.linkweave.api.bookmark;

import java.util.List;

import org.linkweave.api.types.id.ID;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.db.BaseRepo;
import org.linkweave.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
public class AutoTagRuleRepo extends BaseRepo<AutoTagRule> {

    @NonNull
    public List<AutoTagRule> findByCollectionOrderedBySortOrder(@NonNull ID<Collection> collectionId) {
        return db.selectFrom(QAutoTagRule.autoTagRule)
            .where(QAutoTagRule.autoTagRule.collection.id.eq(collectionId.getUUID()))
            .orderBy(QAutoTagRule.autoTagRule.sortOrder.asc())
            .fetch();
    }

    public int findMaxSortOrder(@NonNull ID<Collection> collectionId) {
        return db.select(QAutoTagRule.autoTagRule.sortOrder.max())
            .from(QAutoTagRule.autoTagRule)
            .where(QAutoTagRule.autoTagRule.collection.id.eq(collectionId.getUUID()))
            .fetchOne()
            .orElse(0);
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        var rules = findByCollectionOrderedBySortOrder(collectionId);
        for (var rule : rules) {
            remove(rule.getId());
        }
    }
}
