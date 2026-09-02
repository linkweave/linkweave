package org.linkweave.api.autotag.llm;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.shared.config.ConfigService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a stalled tag-suggestion model from costing anything (UC-108 BR-108-4,
 * BR-108-8, BR-108-9).
 *
 * <p>Three guards, all of which answer in nanoseconds once tripped:
 *
 * <ul>
 *   <li><b>Circuit</b> — after {@code failure-threshold} consecutive failures the
 *       model is left alone for a cooldown. When the cooldown expires exactly one
 *       caller is admitted as a probe; success closes the circuit and resets the
 *       cooldown, failure re-opens it with the cooldown doubled up to its ceiling.</li>
 *   <li><b>Concurrency cap</b> — at most {@code max-concurrent} <em>scopes</em>
 *       are in flight. Overflow is rejected immediately rather than queued,
 *       because a queue in front of a slow model is just a slower way to occupy
 *       the worker pool that also serves bookmark reads and writes.</li>
 *   <li><b>Newest-wins supersede</b> — a permit belongs to a scope (one user in
 *       one collection), not to a call. A newer call for a scope that already has
 *       one takes the permit over rather than taking a second, so a user typing
 *       quickly cannot hold several.</li>
 *   <li><b>Transition logging</b> — health changes log once at WARN/INFO, not once
 *       per call. The 2026-08-28 incident produced eight identical warnings for
 *       what was a single sustained outage, which told the operator how many
 *       bookmarks had been typed rather than what was wrong.</li>
 * </ul>
 *
 * <p>Application-scoped on purpose: the state is per-process model health, not
 * per-request. Deliberately hand-rolled rather than {@code @CircuitBreaker} from
 * SmallRye Fault Tolerance — the cooldown has to grow across re-opens and the
 * outcome has to be reported back to the caller as a status rather than an
 * exception, neither of which the annotation expresses. UC-108's notes flag
 * revisiting this if it grows further.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class LlmCircuitBreaker {

    /** Metric name for suggestion outcomes (BR-108-8, UC-088). */
    static final String SUGGESTIONS_METRIC = "linkweave.autotag.suggestions";

    private final ConfigService config;
    private final MeterRegistry meterRegistry;

    /** Consecutive failures since the last success. Reset to 0 by {@link #recordSuccess}. */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** Epoch millis before which no call may be attempted; 0 means the circuit is closed. */
    private final AtomicLong openUntilEpochMs = new AtomicLong(0);

    /** Current cooldown, doubling per failed probe up to the configured ceiling. */
    private final AtomicLong cooldownMs = new AtomicLong(0);

    /** Guards the single half-open probe so only one caller tests a recovering model. */
    private final AtomicLong probeInFlightUntilEpochMs = new AtomicLong(0);

    /** Last state we logged, so a sustained outage logs once (BR-108-8). */
    private final AtomicReference<Health> lastLoggedHealth = new AtomicReference<>(Health.HEALTHY);

    private Semaphore inFlight;

    /** The admission that currently owns each scope's permit (BR-108-9, newest wins). */
    private final ConcurrentHashMap<String, Admission> inFlightByScope = new ConcurrentHashMap<>();

    /** Gives each admission an identity; see {@link Admission}. */
    private final AtomicLong admissionSeq = new AtomicLong();

    /** Coarse model health, used only to decide whether a transition needs a log line. */
    private enum Health { HEALTHY, DEGRADED }

    @PostConstruct
    void init() {
        int cap = Math.max(1, config.getAutotagMaxConcurrent());
        inFlight = new Semaphore(cap);
        cooldownMs.set(config.getAutotagCircuitCooldown().toMillis());
    }

    /**
     * A permit to call the model, or the reason not to. Released with
     * {@link #release} in a finally block; {@link #rejected} permits carry no
     * resource and releasing them is a no-op, so callers need no special case.
     *
     * <p>{@code seq} exists to give each admission an identity. Records compare
     * by value, and the supersede bookkeeping has to distinguish "this exact
     * admission is still the current one for its scope" from "another admission
     * that happens to look identical" — without it, an older call could release a
     * permit its successor owns.
     */
    public record Admission(
        boolean admitted,
        @NonNull SuggestionOutcome outcome,
        boolean probe,
        @Nullable String scope,
        long seq
    ) {

        static @NonNull Admission rejected(@NonNull SuggestionOutcome outcome) {
            return new Admission(false, outcome, false, null, 0);
        }
    }

    /**
     * Decides whether this caller may contact the model. Never blocks: every path
     * either grants a permit immediately or refuses immediately, which is the
     * whole point of BR-108-9 — a suggestion request must not wait behind another
     * suggestion request.
     *
     * <p>{@code scope} identifies who the call is for — one user in one
     * collection. A second call for a scope that already holds a permit
     * <em>takes over</em> that permit instead of taking another, so the cap counts
     * concurrent users rather than concurrent calls.
     *
     * <p>This matters because a client-side abort does not stop the server. When
     * the user keeps typing, the browser drops its previous request, but the
     * worker thread stays in the blocking model read until the budget expires — so
     * without supersede a single fast typist could hold every permit with work
     * nobody is waiting for any more. Superseding does not cancel the older call
     * (a thread parked in a synchronous HTTP read cannot be reclaimed); it only
     * stops that call from being counted twice. Truly cancelling it would require
     * the model call to be reactive end to end.
     */
    public @NonNull Admission tryAcquire(@NonNull String scope) {
        long now = System.currentTimeMillis();
        long openUntil = openUntilEpochMs.get();
        boolean probe = false;

        if (openUntil > now) {
            return Admission.rejected(SuggestionOutcome.CIRCUIT_OPEN);
        }
        if (openUntil > 0) {
            // Cooldown elapsed: admit exactly one probe (BR-108-4). The circuit is
            // not opened up here — it is re-armed for one interactive budget, so
            // callers arriving behind the probe are still refused. A recovering
            // model should feel one request, not a thundering herd. Winning the CAS
            // is what makes a caller the probe; losers are refused.
            long probeWindowEnd = now + config.getAutotagSuggestTimeoutMs();
            if (!openUntilEpochMs.compareAndSet(openUntil, probeWindowEnd)) {
                return Admission.rejected(SuggestionOutcome.CIRCUIT_OPEN);
            }
            // The window doubles as a dead-man's switch: if the probe somehow never
            // reports back, it expires and the next caller becomes the new probe
            // rather than the circuit staying shut forever.
            probeInFlightUntilEpochMs.set(probeWindowEnd);
            probe = true;
        }
        Admission granted = new Admission(
            true, SuggestionOutcome.OK, probe, scope, admissionSeq.incrementAndGet());
        if (inFlightByScope.put(scope, granted) != null) {
            // The scope already held a permit; this call inherits it. The
            // superseded call will find itself no longer current on release and
            // will not hand the permit back, so the chain still owns exactly one.
            return granted;
        }
        if (!inFlight.tryAcquire()) {
            inFlightByScope.remove(scope, granted);
            return Admission.rejected(SuggestionOutcome.OVERLOADED);
        }
        return granted;
    }

    /**
     * Returns the permit taken by {@link #tryAcquire}. Safe to call for refusals.
     *
     * <p>Only the admission that is still current for its scope owns the permit.
     * A superseded call reaching here has already handed ownership to its
     * successor, so it must not release — doing so would let the cap drift upward
     * by one permit every time a user re-typed.
     */
    public void release(@NonNull Admission admission) {
        if (!admission.admitted()) {
            return;
        }
        String scope = admission.scope();
        if (scope == null || inFlightByScope.remove(scope, admission)) {
            inFlight.release();
        }
    }

    /** Clears the failure streak and closes the circuit, logging the recovery once. */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntilEpochMs.set(0);
        probeInFlightUntilEpochMs.set(0);
        cooldownMs.set(config.getAutotagCircuitCooldown().toMillis());
        logTransition(Health.HEALTHY, "recovered", null);
    }

    /**
     * Counts a failed call and opens the circuit once the threshold is reached.
     *
     * <p>A failed probe re-opens the circuit immediately with the cooldown
     * doubled, without waiting for the threshold again: the probe exists to answer
     * "is it back yet", and "no" is a complete answer.
     */
    public void recordFailure(@NonNull LlmFailure failure) {
        boolean wasProbe = probeInFlightUntilEpochMs.getAndSet(0) > 0;
        int failures = consecutiveFailures.incrementAndGet();

        if (failure == LlmFailure.UNREACHABLE) {
            // A3: nothing is listening. There is no point counting to the
            // threshold against a socket that refuses on contact.
            open(failure, wasProbe);
            return;
        }
        if (wasProbe || failures >= config.getAutotagCircuitFailureThreshold()) {
            open(failure, wasProbe);
        } else {
            LOG.debug("Model call failed ({}); {} consecutive failure(s) so far", failure, failures);
        }
    }

    private void open(@NonNull LlmFailure failure, boolean afterProbe) {
        long cooldown = cooldownMs.get();
        if (afterProbe) {
            cooldown = Math.min(cooldown * 2, config.getAutotagCircuitCooldownMax().toMillis());
            cooldownMs.set(cooldown);
        }
        openUntilEpochMs.set(System.currentTimeMillis() + cooldown);
        logTransition(Health.DEGRADED, failure.name().toLowerCase(java.util.Locale.ROOT),
            Duration.ofMillis(cooldown));
    }

    /** True while the circuit is open, for the "preparing / unavailable" status the client shows. */
    public boolean isOpen() {
        return openUntilEpochMs.get() > System.currentTimeMillis();
    }

    /**
     * One line per health change, never one per call (BR-108-8). Per-call detail
     * stays at DEBUG, where an operator diagnosing a specific bookmark can turn it
     * on without the steady-state log carrying it.
     */
    private void logTransition(@NonNull Health health, @NonNull String reason, Duration cooldown) {
        if (lastLoggedHealth.getAndSet(health) == health) {
            LOG.debug("Model health unchanged ({}, {})", health, reason);
            return;
        }
        if (health == Health.DEGRADED) {
            LOG.warn("Tag suggestions degraded ({}): pausing model calls for {}s. "
                    + "Bookmark saving and rule-based suggestions are unaffected.",
                reason, cooldown == null ? 0 : cooldown.toSeconds());
        } else {
            LOG.info("Tag suggestions recovered; resuming model calls");
        }
    }

    /**
     * Counts one suggestion outcome. Tagged with provider, model and outcome —
     * three bounded dimensions, so the series count stays inside the cardinality
     * budget (NFR-024) no matter how many bookmarks are created.
     */
    public void countOutcome(@NonNull SuggestionOutcome outcome) {
        boolean openAi = config.isAutotagProviderOpenAi();
        Counter.builder(SUGGESTIONS_METRIC)
            .description("Tag-suggestion attempts by outcome")
            .tags(Tags.of(
                "provider", openAi ? "openai" : "ollama",
                "model", openAi ? config.getAutotagOpenAiModel() : config.getAutotagModel(),
                "outcome", outcome.metricValue()))
            .register(meterRegistry)
            .increment();
    }
}
