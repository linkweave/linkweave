package org.linkweave.api.autotag;

import java.time.temporal.ChronoUnit;
import java.util.List;

import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
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
import org.linkweave.api.autotag.llm.BookmarkAutoTagLlmService;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.bookmark.TagMapper;
import org.linkweave.api.bookmark.json.TagListJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.ratelimit.RateLimitConst;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

/**
 * On-demand local-LLM tag suggestion endpoints (FR-095). Nothing is persisted —
 * accepting a suggestion uses the normal tag-apply path (UC-019). When the
 * feature flag is off (FR-096) the service returns no suggestions and the system
 * falls back to client-side rule suggestions.
 */
@RateLimit(value = RateLimitConst.STANDARD_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections/{collectionId}/autotag")
public class BookmarkAutoTagResource {

    private final BookmarkAutoTagLlmService autoTagLlmService;
    private final BookmarkService bookmarkService;
    private final AuthorizationService authorizationService;

    @POST
    @Path("/bookmarks/{bookmarkId}/suggest-tags")
    @RateLimit(value = RateLimitConst.EXTERNAL_LLM_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public TagListJson suggestForBookmark(
        @PathParam("collectionId") @NonNull ID<Collection> collectionId,
        @PathParam("bookmarkId") @NonNull ID<Bookmark> bookmarkId
    ) {
        ID<Collection> owningCollectionId = bookmarkService.getBookmarkCollectionId(bookmarkId);
        authorizationService.requireCollectionAccess(owningCollectionId);
        authorizationService.requireSameCollection(owningCollectionId, collectionId);
        return toList(autoTagLlmService.suggestTagsForBookmark(bookmarkId));
    }

    @POST
    @Path("/suggest-tags")
    @RateLimit(value = RateLimitConst.EXTERNAL_LLM_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public TagListJson suggestForText(
        @PathParam("collectionId") @NonNull ID<Collection> collectionId,
        @NotNull @Valid @NonNull SuggestTagsJson json
    ) {
        authorizationService.requireCollectionAccess(collectionId);
        return toList(autoTagLlmService.suggestTags(
            collectionId, json.getTitle(), json.getUrl(), json.getDescription()));
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
        return autoTagLlmService.warmUp();
    }

    private static @NonNull TagListJson toList(@NonNull List<Tag> tags) {
        return new TagListJson(tags.stream().map(TagMapper::toJson).toList());
    }
}
