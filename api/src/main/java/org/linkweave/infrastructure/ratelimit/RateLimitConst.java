package org.linkweave.infrastructure.ratelimit;

import lombok.experimental.UtilityClass;

/**
 * Shared caps for {@link io.smallrye.faulttolerance.api.RateLimit}.
 *
 * <p><strong>These buckets are process-wide.</strong> SmallRye's {@code @RateLimit} counts
 * invocations per method for the whole JVM — not per user, per session or per IP. Every cap here is
 * therefore shared by all users of the instance at once, which is what makes an apparently generous
 * per-user number far too small in practice: a single SPA boot already costs a handful of calls, so
 * a low class-level cap is exhausted by a few people browsing normally, and everyone else then gets
 * hard failures rather than throttling.</p>
 *
 * <p>Values are per minute (the resources all declare {@code windowUnit = MINUTES}).</p>
 */
@UtilityClass
public class RateLimitConst {

    /**
     * Default cap for authenticated CRUD resources — 20 req/s across the whole process.
     *
     * <p>Sized to leave normal multi-user browsing alone while still bounding a runaway client
     * loop. Endpoints that are genuinely abuse-sensitive (registration, API-key creation) keep
     * their own much stricter method-level caps and deliberately do not use this constant.</p>
     */
    public static final int STANDARD_PER_MINUTE = 1200;
}
