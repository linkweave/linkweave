package org.linkweave.api.autotag.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.net.URL;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.bookmark.TagRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.stereotypes.NoTransactionService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Orchestrates LLM tag suggestion (FR-095). The provider (local Ollama or a
 * hosted OpenAI-compatible API, FR-097) is chosen behind {@link LlmTaggingClient};
 * this service is provider-agnostic. Stateless — nothing is persisted;
 * suggestions are computed on demand and returned to the resource, and only an
 * accepted tag ever persists (via the normal tag-apply path).
 *
 * <p>Authorization is the resource's responsibility; this service trusts its
 * caller (per the layering rules in CLAUDE.md).
 *
 * <p>Non-transactional ({@link NoTransactionService}): the model call blocks for
 * seconds (a cold-start Ollama load, or a hosted round-trip), so running it
 * inside the resource's transaction would pin a DB connection for that whole
 * time. With {@code NOT_SUPPORTED} the caller's transaction is suspended across
 * the HTTP call; the short DB reads (vocabulary, bookmark) run without one, and
 * {@code bookmarkService}/{@code tagRepo} are read just like the screenshot
 * capture job reads its repos.
 */
@NoTransactionService
@RequiredArgsConstructor
@Slf4j
public class BookmarkAutoTagLlmService {

    private final ConfigService configService;
    private final TagRepo tagRepo;
    private final BookmarkService bookmarkService;
    private final LlmTaggingClient llmTaggingClient;

    /**
     * Convenience overload for a persisted bookmark: loads it and extracts the
     * text to classify. Keeping the entity access here (service layer) rather
     * than in the resource respects the layering rules.
     */
    public @NonNull List<Tag> suggestTagsForBookmark(@NonNull ID<Bookmark> bookmarkId) {
        if (!configService.isAutotagLlmEnabled()) {
            return List.of();
        }
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        URL url = bookmark.getUrl();
        return suggestTags(
            bookmark.getCollectionId(),
            bookmark.getTitle(),
            url.toString(),
            bookmark.getDescription());
    }

    /**
     * Suggests existing tags for the given bookmark text, constrained to the
     * collection's tag vocabulary. Returns an empty list when the feature is
     * disabled (FR-096 fallback to rules), the collection has no tags, or the
     * model is unavailable — never throws (best-effort, BR-077).
     */
    public @NonNull List<Tag> suggestTags(
        @NonNull ID<Collection> collectionId,
        @Nullable String title,
        @Nullable String url,
        @Nullable String description
    ) {
        if (!configService.isAutotagLlmEnabled()) {
            return List.of();
        }
        List<Tag> vocabulary = tagRepo.findByCollection(collectionId);
        if (vocabulary.isEmpty()) {
            return List.of();
        }

        // De-dup by name (the unique constraint makes collisions unlikely, but be
        // defensive), preserving order so the prompt's enum is stable.
        Map<String, Tag> byName = new LinkedHashMap<>();
        for (Tag tag : vocabulary) {
            byName.putIfAbsent(tag.getName(), tag);
        }
        List<String> existingTags = List.copyOf(byName.keySet());
        int maxVocabSize = configService.getAutotagMaxVocab();
        if (maxVocabSize > 0 && existingTags.size() > maxVocabSize) {
            existingTags = existingTags.subList(0, maxVocabSize);
        }

        List<String> chosenByLLM;
        try {
            chosenByLLM = llmTaggingClient.suggest(existingTags, buildContent(title, url, description));
        } catch (RuntimeException e) {
            LOG.debug("LLM tag suggestion failed for collection {}: {}", collectionId, e.getMessage());
            return List.of();
        }

        // Re-validate against the vocabulary and map names -> Tag, de-duped,
        // order preserved. Drops anything the model returned that isn't a real
        // tag (belt-and-suspenders against a model ignoring the schema).
        return chosenByLLM.stream()
            .distinct()
            .map(byName::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /** Best-effort warm-up so the next suggestion call isn't cold. Never throws. */
    public void warmUp() {
        if (!configService.isAutotagLlmEnabled()) {
            return;
        }
        try {
            llmTaggingClient.warmUp();
        } catch (RuntimeException e) {
            LOG.debug("LLM warm-up failed: {}", e.getMessage());
        }
    }

    private static @NonNull String buildContent(
        @Nullable String title, @Nullable String url, @Nullable String description) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append("Title: ").append(title).append('\n');
        }
        if (url != null && !url.isBlank()) {
            sb.append("URL: ").append(url).append('\n');
        }
        if (description != null && !description.isBlank()) {
            sb.append("Description: ").append(description).append('\n');
        }
        return sb.toString();
    }
}
