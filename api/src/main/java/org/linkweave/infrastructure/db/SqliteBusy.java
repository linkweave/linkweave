package org.linkweave.infrastructure.db;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

import lombok.experimental.UtilityClass;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * Recognises {@code SQLITE_BUSY} inside whatever a layer happened to throw.
 *
 * <p>Lives apart from {@link SqliteBusyRetryInterceptor} because three decisions now depend on the
 * same question. The interceptor and {@link SqliteBusyRetries} ask it whether to re-run the work;
 * the request boundary asks it whether the failure is transient and therefore a 503 rather than a
 * 500 (BR-109-1). Those run at opposite ends of a request and must never disagree, so the
 * predicate is shared rather than duplicated.
 */
@UtilityClass
public class SqliteBusy {

    /**
     * Whether this failure, or anything it wraps or suppresses, is a BUSY condition.
     *
     * <p>Both links have to be followed. Contention surfaces as a {@code PessimisticLockException}
     * from a read, a Hibernate {@code JDBCException} from a flush, or an Arjuna
     * {@code onePhaseCommit} failure from the commit, so the {@link SQLiteException} is always
     * buried in the cause chain; and a rollback that itself fails attaches the original failure as
     * a <em>suppressed</em> exception rather than a cause.
     *
     * <p>Together those two links form a graph, not a chain, and it can contain cycles — which is
     * not hypothetical: when Mutiny exhausts a retry it calls
     * {@code failure.addSuppressed(new IllegalStateException("Retries exhausted: …", failure))},
     * so the suppressed exception's cause is the failure that suppresses it. {@link SqliteBusyRetries}
     * produces exactly that shape, and a recursive walk over it does not terminate — it cost a
     * {@code StackOverflowError} at the request boundary, which turned a 503 back into the 500 this
     * class exists to prevent. Hence the explicit worklist and the identity-based visited set:
     * equality is deliberately reference identity, because distinct exceptions can be
     * {@code equals} and must still each be examined.
     */
    public static boolean isBusy(Throwable failure) {
        if (failure == null) {
            return false;
        }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.push(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.pop();
            if (!seen.add(current)) {
                continue;
            }
            if (current instanceof SQLiteException sqlite && isBusyCode(sqlite.getResultCode())) {
                return true;
            }
            if (current.getCause() != null) {
                pending.push(current.getCause());
            }
            for (Throwable suppressed : current.getSuppressed()) {
                pending.push(suppressed);
            }
        }
        return false;
    }

    /**
     * Matches any BUSY flavor. Per the SQLite result-code convention
     * (https://sqlite.org/rescode.html), the least significant 8 bits of an extended result code
     * are its primary result code — e.g. {@code SQLITE_BUSY_SNAPSHOT} = 517 = {@code (2 << 8) | 5}
     * extends {@code SQLITE_BUSY} = 5. Masking keeps this future-proof for busy variants this
     * driver version does not know yet.
     */
    static boolean isBusyCode(SQLiteErrorCode resultCode) {
        // Not null-guarded, and deliberately so: this is called from GenericExceptionMapper's
        // catch-all, where an NPE would turn the 503 this exists to produce back into a 500, so
        // the question is worth settling rather than guessing. SQLiteException cannot hold a null
        // result code -- its only constructor reads resultCode.code to build the SQLState before
        // it assigns the field (sqlite-jdbc on the classpath, verified against the bytecode), so a
        // null would fail at construction and never reach this predicate.
        return (resultCode.code & 0xff) == SQLiteErrorCode.SQLITE_BUSY.code;
    }
}
