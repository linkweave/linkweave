package org.linkweave.infrastructure.db;

import java.sql.SQLException;

import jakarta.persistence.PessimisticLockException;
import lombok.experimental.UtilityClass;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * The shapes a {@code SQLITE_BUSY} failure actually arrives in.
 *
 * <p>{@link SqliteBusy} only exists because contention is never thrown bare — every layer wraps it
 * in something of its own — so a test that asserts on contention is worth something only if its
 * input has that structure. Three different components now have to agree about the same inputs
 * (the retry interceptor, the reactive retry policy, and the request-boundary mapper), which makes
 * these shapes a shared fixture rather than per-test scaffolding.</p>
 *
 * <p>Every shape here was observed in the 2026-08-28 incident.</p>
 */
@UtilityClass
public class SqliteBusyFailures {

    /** Contention as the driver first throws it, with nothing wrapped around it. */
    public static SQLiteException bare() {
        return bare(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT);
    }

    public static SQLiteException bare(SQLiteErrorCode code) {
        return new SQLiteException("[" + code.name() + "] database is locked", code);
    }

    /** As a read surfaces it: JPA's lock exception over a JDBC exception over the driver's. */
    public static PessimisticLockException onRead() {
        return new PessimisticLockException("could not obtain lock",
            new SQLException("JDBC exception executing SQL", bare()));
    }

    /** As a flush surfaces it: wrapped by Hibernate, one level down. */
    public static RuntimeException onFlush() {
        return onFlush(SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT);
    }

    public static RuntimeException onFlush(SQLiteErrorCode code) {
        return new RuntimeException("simulated hibernate wrapper", bare(code));
    }

    /**
     * As the commit surfaces it: Arjuna reports a one-phase commit failure and nothing above it
     * knows contention was the reason.
     */
    public static IllegalStateException onCommit() {
        return new IllegalStateException("ARJUNA016039: onePhaseCommit failed",
            new RuntimeException("Error trying to transactionCommit local transaction", bare()));
    }

    /**
     * Reachable only through {@code getSuppressed()}: a rollback that itself fails attaches the
     * original failure there rather than as a cause.
     */
    public static RuntimeException suppressedByFailedRollback() {
        RuntimeException wrapper = new RuntimeException("simulated commit failure");
        wrapper.addSuppressed(bare(SQLiteErrorCode.SQLITE_BUSY));
        return wrapper;
    }
}
