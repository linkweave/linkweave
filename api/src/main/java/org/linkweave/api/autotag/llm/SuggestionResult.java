package org.linkweave.api.autotag.llm;

import java.util.List;

import org.linkweave.api.bookmark.Tag;
import org.jspecify.annotations.NonNull;

/**
 * What {@link BookmarkAutoTagLlmService} produced: the suggested tags, and why
 * the list looks the way it does (UC-108 BR-108-5).
 *
 * <p>Carries entities rather than DTOs because mapping to JSON belongs to the
 * resource layer. The outcome travels with the tags rather than being inferred
 * from an empty list — "the model had nothing to suggest" and "the model never
 * answered" are the same list and different situations.
 */
public record SuggestionResult(@NonNull List<Tag> tags, @NonNull SuggestionOutcome outcome) {

    public static @NonNull SuggestionResult of(
        @NonNull List<Tag> tags, @NonNull SuggestionOutcome outcome) {
        return new SuggestionResult(tags, outcome);
    }

    public static @NonNull SuggestionResult none(@NonNull SuggestionOutcome outcome) {
        return new SuggestionResult(List.of(), outcome);
    }
}
