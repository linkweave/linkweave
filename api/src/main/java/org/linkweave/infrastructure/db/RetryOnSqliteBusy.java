package org.linkweave.infrastructure.db;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Re-invokes the annotated method when it fails with {@code SQLITE_BUSY}, most notably the
 * {@code SQLITE_BUSY_SNAPSHOT} flavor: a deferred transaction that read from a stale WAL snapshot
 * can never upgrade to a writer and fails immediately, without waiting for {@code busy_timeout}.
 * Waiting inside the failed transaction cannot help — the whole transaction must roll back and
 * re-execute — so this binding must sit <b>outside</b> the transaction boundary (see
 * {@link SqliteBusyRetryInterceptor} for the interceptor ordering).
 *
 * <p>Only apply to methods that are safe to re-execute after a full rollback, i.e. methods
 * without non-transactional side effects (external HTTP calls, mails, file writes).</p>
 */
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Documented
public @interface RetryOnSqliteBusy {

    /** Total attempts including the initial one; values below 1 are treated as 1. */
    @Nonbinding
    int attempts() default 5;
}
