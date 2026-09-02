package org.linkweave.api.autotag;

import java.time.temporal.ChronoUnit;
import java.util.List;

import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.autotag.json.AutotagLLMProviderJson;
import org.linkweave.api.autotag.json.SuggestTagsJson;
import org.linkweave.api.autotag.json.SuggestedTagsResultJson;
import org.linkweave.api.autotag.json.SuggestionStatusJson;
import org.linkweave.api.autotag.llm.BookmarkAutoTagLlmService;
import org.linkweave.api.autotag.llm.SuggestionResult;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.bookmark.TagMapper;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionService;
import org.linkweave.api.shared.auth.AuthorizationService;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.ratelimit.RateLimitConst;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

/**
 * On-demand local-LLM tag suggestion endpoints (FR-095). Nothing is persisted —
 * accepting a suggestion uses the normal tag-apply path (UC-019). When the
 * feature flag is off (FR-096) the service returns no suggestions and the system
 * falls back to client-side rule suggestions.
 *
 * <p><b>Deliberately outside a transaction</b> (UC-108 BR-108-3). {@code @JaxResource}
 * contributes {@code @Transactional(REQUIRED)}, which is wrong for exactly this
 * resource: the guard clauses below read the DB, so a connection is acquired and
 * enlisted in the resource transaction, and {@code BookmarkAutoTagLlmService} being
 * {@code NOT_SUPPORTED} only <em>suspends</em> that transaction — Agroal keeps the
 * connection checked out until the transaction completes. A stalled model therefore
 * pinned one pool connection per in-flight suggestion for the full read-timeout, and
 * with auto-fire firing a suggestion for every bookmark the user typed, the pool
 * drained and unrelated requests — including bookmark saves — blocked in
 * {@code getConnection()}. That is the "save button stalls while tags are in flight"
 * report of 2026-08-28, and the other half of the SQLITE_BUSY storm in UC-109.
 *
 * <p>With {@code NOT_SUPPORTED} here, each collaborator below opens its own short
 * transaction ({@code @Service} is {@code REQUIRED}) and commits it, returning the
 * connection to the pool <em>before</em> the model is called. Regression-tested by
 * {@code BookmarkAutoTagStallITest}.
 */
@Transactional(Transactional.TxType.NOT_SUPPORTED)
@RateLimit(value = RateLimitConst.STANDARD_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/autotag")
public class BookmarkAutoTagResource {

    private final BookmarkAutoTagLlmService autoTagLlmService;
    private final BookmarkService bookmarkService;
    private final AuthorizationService authorizationService;
    private final CurrentUserService currentUserService;
    private final CollectionService collectionService;

    @POST
    @Path("/bookmarks/{bookmarkId}/suggest-tags")
    @RateLimit(value = RateLimitConst.EXTERNAL_LLM_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public SuggestedTagsResultJson suggestForBookmark(
        @PathParam("collectionId") @NonNull ID<Collection> collectionId,
        @PathParam("bookmarkId") @NonNull ID<Bookmark> bookmarkId
    ) {
        ID<Collection> owningCollectionId = bookmarkService.getBookmarkCollectionId(bookmarkId);
        authorizationService.requireCollectionAccess(owningCollectionId);
        authorizationService.requireSameCollection(owningCollectionId, collectionId);
        if (!collectionService.isAiTaggingEnabled(owningCollectionId)) {
            return disabledForCollection();
        }
        return toJson(autoTagLlmService.suggestTagsForBookmark(bookmarkId, scopeOf(owningCollectionId)));
    }

    @POST
    @Path("/suggest-tags")
    @RateLimit(value = RateLimitConst.EXTERNAL_LLM_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public SuggestedTagsResultJson suggestForText(
        @PathParam("collectionId") @NonNull ID<Collection> collectionId,
        @NotNull @Valid @NonNull SuggestTagsJson json
    ) {
        authorizationService.requireCollectionAccess(collectionId);
        if (!collectionService.isAiTaggingEnabled(collectionId)) {
            return disabledForCollection();
        }
        return toJson(autoTagLlmService.suggestTags(
            collectionId, json.getTitle(), json.getUrl(), json.getDescription(),
            scopeOf(collectionId)));
    }

    /**
     * Keeps the standard cap on purpose, unlike its metered neighbours above.
     *
     * <p>Warm-up reads as the expensive endpoint and isn't one. It returns immediately when the
     * provider is OpenAI, so it never bills anything; on Ollama it sends a keep-alive request with
     * no prompt (load the model, don't infer), and the one genuinely costly path — the first model
     * pull — is already serialized by a compare-and-set so concurrent callers fast-fail rather than
     * downloading in parallel.</p>
     *
     * <p>It is also the most frequently called endpoint here: {@code SuggestedTagsSection} fires it
     * every time the bookmark dialog opens. Capping it like a costly operation only rejects normal
     * browsing — a trial 10/min cap produced 13 rejections in one e2e run, and even 300/min was not
     * enough.</p>
     */
    @POST
    @Path("/warm-up")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public AutotagLLMProviderJson warmUp(@PathParam("collectionId") @NonNull ID<Collection> collectionId) {
        authorizationService.requireCollectionAccess(collectionId);
        // Gating warm-up matters as much as gating the suggestion calls (UC-112
        // BR-112-3): the dialog calls this on every open, so an opted-out
        // collection would otherwise still be loading the model onto the host --
        // no bookmark text would leave, but the work would still be done.
        return autoTagLlmService.warmUp(collectionService.isAiTaggingEnabled(collectionId));
    }

    /**
     * The answer for a collection that has opted out (UC-112 BR-112-3/BR-112-7).
     * Reuses the status UC-108 already defined for a switched-off feature, so a
     * caller that skips warm-up is still refused on the endpoint it calls.
     */
    private static @NonNull SuggestedTagsResultJson disabledForCollection() {
        return new SuggestedTagsResultJson(List.of(), SuggestionStatusJson.DISABLED);
    }

    /**
     * Identifies the dialog this call is for, so the concurrency cap counts users
     * rather than keystrokes (UC-108 BR-108-9). One user editing in one collection
     * is one scope: when they keep typing, the newer request takes over the older
     * one's slot instead of claiming a second, because the browser has already
     * abandoned the older one.
     */
    private @NonNull String scopeOf(@NonNull ID<Collection> collectionId) {
        return currentUserService.currentUserID() + ":" + collectionId;
    }

    /** Entity -> DTO mapping stays in the resource layer, per the layering rules. */
    private static @NonNull SuggestedTagsResultJson toJson(@NonNull SuggestionResult result) {
        return new SuggestedTagsResultJson(
            result.tags().stream().map(TagMapper::toJson).toList(),
            result.outcome().toStatus());
    }
}
