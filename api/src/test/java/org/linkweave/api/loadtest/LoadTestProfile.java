package org.linkweave.api.loadtest;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for {@link SqliteWriteContentionLoadITest}.
 *
 * <p>Two deliberate overrides vs. the normal {@code %test} configuration:</p>
 * <ul>
 *   <li><b>Disable SmallRye Fault Tolerance</b> — every resource class is annotated
 *       {@code @RateLimit(value = RateLimitConst.STANDARD_PER_MINUTE, window = 1,
 *       windowUnit = MINUTES)} — a process-wide bucket. Without disabling FT,
 *       a concurrent load test would be flooded with HTTP 429s long before any {@code SQLITE_BUSY}
 *       contention appears. FT is used in this codebase <em>only</em> for {@code @RateLimit}, so
 *       disabling it has no other side effects.</li>
 *   <li><b>Configurable SQLite {@code busy_timeout}</b> — defaults to the production value
 *       (10000&nbsp;ms) so the baseline numbers are honest. Override via the
 *       {@code LINKWEAVE_LOADTEST_BUSY_TIMEOUT_MS} environment variable with a small value
 *       (e.g. {@code 250}) to make {@code SQLITE_BUSY} surface as 5xx errors quickly instead of as
 *       long writer-queue latency.</li>
 * </ul>
 *
 * <p>The journal mode defaults to the rollback-journal baseline (UC-095 step 1). Set
 * {@code LINKWEAVE_LOADTEST_WAL=true} for {@code journal_mode=WAL&synchronous=NORMAL}, or
 * {@code LINKWEAVE_LOADTEST_WAL=immediate} to additionally add {@code transaction_mode=IMMEDIATE}
 * (warning: with sqlite-jdbc + Agroal this makes idle pooled connections hold the write lock —
 * see the UC-095 before/after measurements). Note: WAL mode persists in the db file, so delete
 * {@code linkweave-test.db*} between runs that switch modes ({@code journal_mode=DELETE} is
 * passed explicitly for the baseline as a belt-and-braces measure).</p>
 *
 * <p>All settings can be supplied as either environment variables or {@code -D} system properties
 * (system properties take precedence). Surefire reliably forwards {@code -D} flags to the forked
 * test JVM; env-var propagation depends on the parent shell. Pool sizing is exposed via
 * {@code LINKWEAVE_LOADTEST_MAX_SIZE} / {@code LINKWEAVE_LOADTEST_MIN_SIZE} for the UC-095 option-B
 * experiments — when unset, Agroal's default (50, see {@code DataSourceJdbcRuntimeConfig#maxSize})
 * is used.</p>
 */
public class LoadTestProfile implements QuarkusTestProfile {

    public static final String ENV_BUSY_TIMEOUT = "LINKWEAVE_LOADTEST_BUSY_TIMEOUT_MS";
    public static final String ENV_WAL = "LINKWEAVE_LOADTEST_WAL";
    public static final String ENV_MAX_SIZE = "LINKWEAVE_LOADTEST_MAX_SIZE";
    public static final String ENV_MIN_SIZE = "LINKWEAVE_LOADTEST_MIN_SIZE";
    public static final String ENV_ACQUISITION_TIMEOUT = "LINKWEAVE_LOADTEST_ACQUISITION_TIMEOUT_MS";

    /**
     * Read a setting from either an environment variable or a system property (system property
     * wins). Surefire reliably forwards {@code -D} flags to the forked test JVM, but env-var
     * propagation is unreliable in some shells/CI environments — so both paths are supported.
     */
    private static String setting(String name) {
        String prop = System.getProperty(name);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return System.getenv(name);
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        String busy = setting(ENV_BUSY_TIMEOUT);
        if (busy == null || busy.isBlank()) {
            busy = "10000";
        }
        String walMode = setting(ENV_WAL);
        String journal;
        if ("immediate".equalsIgnoreCase(walMode)) {
            journal = "&journal_mode=WAL&transaction_mode=IMMEDIATE&synchronous=NORMAL";
        } else if ("true".equalsIgnoreCase(walMode)) {
            journal = "&journal_mode=WAL&synchronous=NORMAL";
        } else {
            journal = "&journal_mode=DELETE";
        }
        String jdbcUrl = "jdbc:sqlite:linkweave-test.db?foreign_keys=on&busy_timeout=" + busy + journal;

        Map<String, String> config = new HashMap<>();
        config.put("MP_Fault_Tolerance_NonFallback_Enabled", "false");
        config.put("smallrye.faulttolerance.enabled", "false");
        config.put("quarkus.datasource.jdbc.url", jdbcUrl);

        // Pool sizing — option B experiments. unset = Agroal default (50).
        String maxSize = setting(ENV_MAX_SIZE);
        if (maxSize != null && !maxSize.isBlank()) {
            config.put("quarkus.datasource.jdbc.max-size", maxSize);
        }
        String minSize = setting(ENV_MIN_SIZE);
        if (minSize != null && !minSize.isBlank()) {
            config.put("quarkus.datasource.jdbc.min-size", minSize);
        }
        String acquisitionTimeout = setting(ENV_ACQUISITION_TIMEOUT);
        if (acquisitionTimeout != null && !acquisitionTimeout.isBlank()) {
            config.put("quarkus.datasource.jdbc.acquisition-timeout", acquisitionTimeout);
        }
        return config;
    }
}
