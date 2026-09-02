package org.linkweave.api.autotag.json;

/**
 * What the bookmark dialog should say about tag suggestions (UC-108 BR-108-5).
 *
 * <p>Exists because {@link #EMPTY} and {@link #UNAVAILABLE} used to be
 * indistinguishable on the wire: the endpoint returned a bare tag list, so "the
 * model found nothing" and "the model never answered" both arrived as
 * {@code []}. The dialog showed the same empty state for both, which is the
 * silent-total-loss defect in UC-108's background — the feature would go dark
 * after one timeout with nothing to tell the user why.
 */
public enum SuggestionStatusJson {

    /** Suggestions were produced. */
    OK,

    /** The model answered and had nothing confident to suggest (UC-097 A4). */
    EMPTY,

    /** The model could not be reached or did not answer in time; rules still work. */
    UNAVAILABLE,

    /** The model is being downloaded; suggestions return on their own (A2). */
    PREPARING,

    /** Auto-tagging is switched off for this installation (FR-096). */
    DISABLED
}
