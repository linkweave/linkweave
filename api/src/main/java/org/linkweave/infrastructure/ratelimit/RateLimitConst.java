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

    /**
     * Cap for endpoints that forward to an external LLM.
     *
     * <p>{@link #STANDARD_PER_MINUTE} is sized for cheap local CRUD, where the only thing a flood
     * costs is CPU. These endpoints are different: with
     * {@code linkweave.autotag.provider=openai} every call bills a third-party API, so the limit
     * is the operator's spend cap, not a throughput knob. Kept deliberately close to what a person
     * clicking "suggest tags" can produce.</p>
     */
    public static final int EXTERNAL_LLM_PER_MINUTE = 60;

    /**
     * Cap for endpoints where one screenful of UI legitimately produces a burst of calls, or where
     * the annotation is present only because it is mandatory.
     *
     * <p>{@link #STANDARD_PER_MINUTE} is the wrong size for these. A single collection view fans
     * out one screenshot request per visible bookmark, and the live-update stream must never be the
     * thing that locks a user out of the API (BR-208) — yet
     * {@code JaxResourceTest.enforce_rate_limit_on_all_methods} makes omitting the annotation
     * impossible. Deliberately high enough to be unreachable by real usage while still bounding a
     * runaway client, since the bucket is process-wide and a lockout here hits everyone.</p>
     */
    public static final int HIGH_FANOUT_PER_MINUTE = 15000;

    // Deliberately no separate warm-up cap — see BookmarkAutoTagResource#warmUp for why it keeps
    // STANDARD_PER_MINUTE despite living next to the metered endpoints.
}
