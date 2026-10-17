package org.linkweave.infrastructure.db;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counts JDBC statements for a test, by switching Hibernate statistics on for just that test.
 *
 * <p>{@code quarkus.hibernate-orm.statistics} used to be set for the whole {@code %test} profile so
 * that one assertion could count statements. Every other integration test paid per-statement
 * bookkeeping for it, and {@code SqliteWriteContentionLoadITest} measures timings it perturbs. A
 * {@code QuarkusTestProfile} would scope it at the cost of a second Quarkus boot, whereas the flag
 * is plain runtime state on the shared {@code SessionFactory} — so a test flips it and puts it
 * back.</p>
 *
 * <p>Shared by the two identity-decorator tests, which both need to prove that re-subscribing a
 * lookup really goes back to the database rather than replaying a memoised result.</p>
 */
final class HibernateStatisticsProbe {

    private final EntityManagerFactory entityManagerFactory;
    private boolean wasEnabled;

    HibernateStatisticsProbe(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    void enable() {
        Statistics statistics = statistics();
        wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
    }

    void restore() {
        statistics().setStatisticsEnabled(wasEnabled);
    }

    /**
     * JDBC statements executed so far. Asserted to be enabled rather than assumed: with statistics
     * off this counter stays at zero and every comparison against it would pass vacuously.
     */
    long statements() {
        Statistics statistics = statistics();
        assertThat(statistics.isStatisticsEnabled())
            .as("enable() must have switched Hibernate statistics on for this to measure anything")
            .isTrue();
        return statistics.getPrepareStatementCount();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
