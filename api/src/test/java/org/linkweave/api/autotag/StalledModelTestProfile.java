package org.linkweave.api.autotag;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Shrinks the JDBC pool so {@link BookmarkAutoTagStallITest} can exhaust it with
 * a handful of threads instead of the twenty-odd the default would need.
 *
 * <p>This is not an artificial setup — it is the production failure scaled down.
 * In production the pool has 20 connections and auto-fire sends a suggestion for
 * every bookmark being typed; here it has 2 and the test sends four. The ratio,
 * and the defect, are the same: if a stalled model can hold a pooled connection,
 * enough concurrent suggestions will starve every other request of one.
 */
public class StalledModelTestProfile implements QuarkusTestProfile {

    /** Pool size the test saturates. Two hung suggestions must not be able to claim both. */
    public static final String MAX_POOL_SIZE = "2";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.datasource.jdbc.max-size", MAX_POOL_SIZE,
            "quarkus.datasource.jdbc.acquisition-timeout", "5");
    }
}
