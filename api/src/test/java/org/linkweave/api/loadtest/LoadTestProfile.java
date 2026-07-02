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
 *       {@code @RateLimit(value = 120, window = 1, windowUnit = MINUTES)}. Without disabling FT,
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
 */
public class LoadTestProfile implements QuarkusTestProfile {

    public static final String ENV_BUSY_TIMEOUT = "LINKWEAVE_LOADTEST_BUSY_TIMEOUT_MS";
    public static final String ENV_WAL = "LINKWEAVE_LOADTEST_WAL";

    @Override
    public Map<String, String> getConfigOverrides() {
        String busy = System.getenv(ENV_BUSY_TIMEOUT);
        if (busy == null || busy.isBlank()) {
            busy = "10000";
        }
        String walMode = System.getenv(ENV_WAL);
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
        return config;
    }
}
