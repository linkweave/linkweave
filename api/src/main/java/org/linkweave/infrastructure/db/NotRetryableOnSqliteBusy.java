package org.linkweave.infrastructure.db;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares that a resource deliberately has <b>no</b> {@link RetryOnSqliteBusy} boundary, because
 * re-executing it after a rollback would be wrong rather than merely slow.
 *
 * <p>Retrying is only safe for work with no non-transactional side effects. A resource that posts
 * to an external service, bills an API call, or streams a long-lived response cannot be re-run from
 * the top: the rollback undoes the database half and nothing else. For those, contention is still
 * handled — {@code GenericExceptionMapper} classifies {@code SQLITE_BUSY} as a transient condition
 * and answers 503 with {@code Retry-After} (BR-109-1) — it is simply not <em>retried</em> for the
 * caller.</p>
 *
 * <p>This exists so that the architecture rule enforcing retry coverage (BR-109-2,
 * {@code JaxResourceTest#enforce_sqlite_busy_retry_boundary_on_all_resources}) can tell a
 * considered exemption from an oversight. {@link #because()} is mandatory for the same reason: the
 * next person needs the argument, not just the exemption.</p>
 *
 * <p>Named for what it asserts about the endpoint rather than as an {@code IgnoreFor…Test} opt-out
 * like {@code IgnoreForIdClassTest}: this is a statement about correctness that happens to be
 * checked by a test, not a silenced lint.</p>
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
public @interface NotRetryableOnSqliteBusy {

    /** Why re-executing this resource after a rollback would be incorrect. */
    String because();
}
