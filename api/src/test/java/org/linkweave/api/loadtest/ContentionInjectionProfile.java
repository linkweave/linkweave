package org.linkweave.api.loadtest;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Profile for {@link SqliteBusyContentionInjectionITest}.
 *
 * <p>Rollback-journal mode with a 1&nbsp;ms {@code busy_timeout}. The point of the test is to
 * observe what a request <em>does</em> when the lock cannot be taken, and with the production
 * 10&nbsp;s timeout the driver would simply
 * wait out any lock a test can realistically hold, so the failure would never surface. Failing
 * immediately is not an artificial condition either — {@code SQLITE_BUSY_SNAPSHOT} ignores
 * {@code busy_timeout} altogether, which is the flavour UC-109's incident hit.</p>

 * <p>Two traps this configuration exists to avoid, both of which silently turned an early version
 * of the test green for the wrong reason:</p>
 * <ul>
 *   <li>{@code busy_timeout=0} does not mean "fail immediately" here — sqlite-jdbc treats it as
 *       unset and applies its own 3000&nbsp;ms default, so every contended request just waited
 *       three seconds and then succeeded.</li>
 *   <li>In WAL mode the lock cannot be injected at all: WAL readers take no database lock, so an
 *       exclusive writer leaves every read untouched — and the identity lookup is a read.
 *       Rollback-journal is the mode in which a writer actually blocks readers on demand.
 *       It runs against its own database file for that reason: journal mode persists in the file,
 *       so sharing linkweave-test.db would leak this mode into the rest of the suite.
 *       In WAL the same failure arises from checkpointing or WAL recovery — attested by the
 *       incident log in UC-109, but not something a test can schedule. What is asserted here is
 *       what a request does <em>given</em> a BUSY read, which does not depend on the journal mode
 *       that produced it.</li>
 * </ul>
 *
 * <p>Fault tolerance is disabled for the same reason as {@link LoadTestProfile}: {@code @RateLimit}
 * is a process-wide bucket, and the repeated {@code /auth/me} calls here would otherwise be
 * answered with 429 before contention mattered.</p>
 */
public class ContentionInjectionProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        config.put("MP_Fault_Tolerance_NonFallback_Enabled", "false");
        config.put("smallrye.faulttolerance.enabled", "false");
        config.put("quarkus.datasource.jdbc.url",
            "jdbc:sqlite:linkweave-contention-test.db"
                + "?foreign_keys=on&busy_timeout=1&journal_mode=DELETE");
        return config;
    }
}
