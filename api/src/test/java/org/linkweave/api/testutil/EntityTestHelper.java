package org.linkweave.api.testutil;

import java.time.OffsetDateTime;
import org.linkweave.api.shared.abstractentity.AbstractEntity;
import org.jspecify.annotations.NonNull;

public final class EntityTestHelper {

    private EntityTestHelper() {
    }

    public static final String DEFAULT_USER = "test@example.com";

    @NonNull
    public static <E extends AbstractEntity<E>> E initEntityInfo(E entity) {
        OffsetDateTime now = OffsetDateTime.now();
        entity.setTimestampErstellt(now);
        entity.setTimestampMutiert(now);
        entity.setUserErstellt(DEFAULT_USER);
        entity.setUserMutiert(DEFAULT_USER);
        return entity;
    }
}
