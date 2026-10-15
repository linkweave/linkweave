package org.linkweave.infrastructure.db;

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
     * <p>The cause chain matters: contention surfaces as a {@code PessimisticLockException} from a
     * read, a Hibernate {@code JDBCException} from a flush, or an Arjuna {@code onePhaseCommit}
     * failure from the commit — the {@link SQLiteException} is always buried. Suppressed
     * exceptions are searched too, because a failed rollback attaches the original failure there.
     * Self-referencing causes are guarded against.</p>
     */
    public static boolean isBusy(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLiteException sqlite && isBusyCode(sqlite.getResultCode())) {
                return true;
            }
            for (Throwable suppressed : t.getSuppressed()) {
                if (isBusy(suppressed)) {
                    return true;
                }
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
        return (resultCode.code & 0xff) == SQLiteErrorCode.SQLITE_BUSY.code;
    }
}
