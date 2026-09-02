package org.linkweave.api.autotag.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.net.URL;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.linkweave.api.autotag.json.AutotagLLMProviderJson;
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
 * seconds (a cold-start Ollama load, or a hosted round-trip), and a DB connection
 * held across it is a connection the rest of the application cannot have.
 *
 * <p>{@code NOT_SUPPORTED} here is necessary but was not sufficient, which is
 * worth spelling out because the gap caused a production incident. Suspending a
 * transaction does not return its connection to the pool — Agroal keeps it
 * enlisted until the transaction completes — so while {@code BookmarkAutoTagResource}
 * still carried {@code @Transactional(REQUIRED)} from its stereotype, its
 * authorization read acquired a connection that stayed checked out for the whole
 * model call. The fix is on the resource (UC-108 BR-108-3); this annotation keeps
 * the service from re-introducing the problem from below.
 */
@NoTransactionService
@RequiredArgsConstructor
@Slf4j
public class BookmarkAutoTagLlmService {

    private final ConfigService configService;
    private final TagRepo tagRepo;
    private final BookmarkService bookmarkService;
    private final LlmTaggingClient llmTaggingClient;
    private final ManagedExecutor managedExecutor;

    /**
     * Convenience overload for a persisted bookmark: loads it and extracts the
     * text to classify. Keeping the entity access here (service layer) rather
     * than in the resource respects the layering rules.
     */
    public @NonNull SuggestionResult suggestTagsForBookmark(
        @NonNull ID<Bookmark> bookmarkId, @NonNull String scope) {
        if (!configService.isAutotagLlmEnabled()) {
            return SuggestionResult.none(SuggestionOutcome.DISABLED);
        }
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        URL url = bookmark.getUrl();
        return suggestTags(
            bookmark.getCollectionId(),
            bookmark.getTitle(),
            url.toString(),
            bookmark.getDescription(),
            scope);
    }

    /**
     * Suggests existing tags for the given bookmark text, constrained to the
     * collection's tag vocabulary. Returns an empty list when the feature is
     * disabled (FR-096 fallback to rules), the collection has no tags, or the
     * model is unavailable — never throws (best-effort, BR-077).
     */
    public @NonNull SuggestionResult suggestTags(
        @NonNull ID<Collection> collectionId,
        @Nullable String title,
        @Nullable String url,
        @Nullable String description,
        @NonNull String scope
    ) {
        if (!configService.isAutotagLlmEnabled()) {
            return SuggestionResult.none(SuggestionOutcome.DISABLED);
        }
        List<Tag> vocabulary = tagRepo.findByCollection(collectionId);
        if (vocabulary.isEmpty()) {
            // Nothing to choose from. Reported as EMPTY rather than UNAVAILABLE:
            // the feature is working, this collection just has no tags yet.
            return SuggestionResult.none(SuggestionOutcome.EMPTY);
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

        LlmTaggingClient.Result result;
        try {
            result = llmTaggingClient.suggest(
                existingTags, buildContent(title, url, description), scope);
        } catch (RuntimeException e) {
            // The client is expected to report degradation as an outcome rather
            // than throw, but a fake or a future provider might not; best-effort
            // (BR-077) means the dialog still gets an answer either way.
            LOG.debug("LLM tag suggestion failed for collection {}: {}", collectionId, e.getMessage());
            return SuggestionResult.none(SuggestionOutcome.UNAVAILABLE);
        }

        // Re-validate against the vocabulary and map names -> Tag, de-duped,
        // order preserved. Drops anything the model returned that isn't a real
        // tag (belt-and-suspenders against a model ignoring the schema).
        List<Tag> tags = result.tagNames().stream()
            .distinct()
            .map(byName::get)
            .filter(Objects::nonNull)
            .toList();

        // A model that answered but whose every pick was filtered out is EMPTY,
        // not OK — otherwise the dialog claims a successful suggestion and shows
        // nothing.
        SuggestionOutcome outcome = result.outcome() == SuggestionOutcome.OK && tags.isEmpty()
            ? SuggestionOutcome.EMPTY
            : result.outcome();
        return SuggestionResult.of(tags, outcome);
    }

    /**
     * Returns the active provider/model so the frontend badge can label itself
     * (FR-097), and kicks off a best-effort model preload so the next suggestion
     * call isn't cold. The preload runs off the request thread: the first-ever
     * Ollama run pulls the model (minutes), and the badge shouldn't wait on it.
     * The provider info is config-derived, so it's returned immediately. Never
     * throws.
     */
    public @NonNull AutotagLLMProviderJson warmUp(boolean collectionEnabled) {
        if (collectionEnabled && configService.isAutotagLlmEnabled()) {
            managedExecutor.execute(() -> {
                try {
                    llmTaggingClient.warmUp();
                } catch (RuntimeException e) {
                    LOG.debug("LLM warm-up failed: {}", e.getMessage());
                }
            });
        }
        return providerInfo(collectionEnabled);
    }

    /**
     * Active provider descriptor, derived purely from config (no model call).
     *
     * <p>{@code enabled} is the conjunction of the operator's flag and the
     * collection's (UC-112 BR-112-1) — the two are in series, and the client only
     * ever needs to know the answer, not which of the two said no.
     */
    private @NonNull AutotagLLMProviderJson providerInfo(boolean collectionEnabled) {
        boolean openAi = configService.isAutotagProviderOpenAi();
        String model = openAi
            ? configService.getAutotagOpenAiModel()
            : configService.getAutotagModel();
        return new AutotagLLMProviderJson(
            openAi ? "openai" : "ollama",
            model,
            !openAi,
            collectionEnabled && configService.isAutotagLlmEnabled(),
            configService.isAutotagAutoFire(),
            configService.getAutotagSuggestTimeoutMs());
    }
    @NonNull
    private static  String buildContent(
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
