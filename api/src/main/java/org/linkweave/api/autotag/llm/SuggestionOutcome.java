package org.linkweave.api.autotag.llm;

import org.linkweave.api.autotag.json.SuggestionStatusJson;
import org.jspecify.annotations.NonNull;

/**
 * How one tag-suggestion attempt ended (UC-108 BR-108-5, BR-108-8).
 *
 * <p>Two audiences, deliberately served by one enum so they cannot drift apart:
 *
 * <ul>
 *   <li>The <b>operator</b> gets {@link #metricValue()} — the fine-grained cause,
 *       because "the circuit is open" and "the model timed out" call for
 *       different responses.</li>
 *   <li>The <b>user</b> gets {@link #toStatus()} — the collapsed form, because
 *       every way the model can fail to answer means the same thing to someone
 *       filling in a bookmark dialog.</li>
 * </ul>
 *
 * <p>The distinction that matters to the user is {@link #EMPTY} versus the
 * failures: "the model looked and found nothing that fits" is a real answer,
 * "the model never answered" is not, and showing the same empty state for both
 * (as the dialog did before UC-108) leaves them re-typing a title to coax
 * suggestions out of a model that is not running.
 */
public enum SuggestionOutcome {

    /** The model answered with at least one usable tag. */
    OK("ok", SuggestionStatusJson.OK),

    /** The model answered, and nothing in the vocabulary fit (UC-097 A4). */
    EMPTY("empty", SuggestionStatusJson.EMPTY),

    /** The model did not answer inside the interactive budget (BR-108-1). */
    TIMEOUT("timeout", SuggestionStatusJson.UNAVAILABLE),

    /** The service is unreachable, erroring, or answering nonsense. */
    UNAVAILABLE("unavailable", SuggestionStatusJson.UNAVAILABLE),

    /** Refused without contacting the model because the circuit is open (BR-108-4). */
    CIRCUIT_OPEN("circuit_open", SuggestionStatusJson.UNAVAILABLE),

    /** Refused because the concurrency cap was already taken (BR-108-9). */
    OVERLOADED("overloaded", SuggestionStatusJson.UNAVAILABLE),

    /** A pull is running; the feature is coming back on its own (A2). */
    PREPARING("preparing", SuggestionStatusJson.PREPARING),

    /** The feature is switched off by config (FR-096) — not a failure. */
    DISABLED("disabled", SuggestionStatusJson.DISABLED);

    private final String metricValue;
    private final SuggestionStatusJson status;

    SuggestionOutcome(@NonNull String metricValue, @NonNull SuggestionStatusJson status) {
        this.metricValue = metricValue;
        this.status = status;
    }

    /** Low-cardinality tag value for the outcome counter (BR-108-8, NFR-024). */
    public @NonNull String metricValue() {
        return metricValue;
    }

    /** The user-facing collapse of this outcome. */
    public @NonNull SuggestionStatusJson toStatus() {
        return status;
    }
}
