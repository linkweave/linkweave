package org.chainlink.api.cleanup;

import java.time.temporal.ChronoUnit;
import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.cleanup.json.CleanupSuggestionListJson;
import org.chainlink.api.cleanup.json.CleanupThresholdsJson;
import org.chainlink.api.cleanup.json.MoveToTrashJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/cleanup-suggestions")
public class CleanupSuggestionResource {

    private final CleanupSuggestionService cleanupSuggestionService;
    private final BookmarkService bookmarkService;
    private final AuthorizationService authorizationService;

    private int resolveThreshold(Integer thresholdMonths) {
        int defaultThreshold = cleanupSuggestionService.getDefaultThreshold();
        int months = thresholdMonths != null ? thresholdMonths : defaultThreshold;
        if (!cleanupSuggestionService.isValidThreshold(months)) {
            return defaultThreshold;
        }
        return months;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public CleanupSuggestionListJson list(
        @QueryParam("collectionId") @NotNull @NonNull ID<Collection> collectionId,
        @QueryParam("thresholdMonths") Integer thresholdMonths
    ) {
        authorizationService.requireCollectionAccess(collectionId);
        return new CleanupSuggestionListJson(
            cleanupSuggestionService.getSuggestions(collectionId, resolveThreshold(thresholdMonths))
        );
    }

    @GET
    @Path("/thresholds")
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public CleanupThresholdsJson thresholds() {
        return new CleanupThresholdsJson(cleanupSuggestionService.getAvailableThresholds());
    }

    @POST
    @Path("/{bookmarkId}/dismiss")
    @Authenticated
    public void dismiss(@PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId) {
        Bookmark bookmark = bookmarkService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        cleanupSuggestionService.dismissSuggestion(bookmarkId);
    }

    @POST
    @Path("/move-to-trash")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    public void moveToTrash(@NotNull @Valid @NonNull MoveToTrashJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        List<Bookmark> bookmarks = bookmarkService.getBookmarks(json.getBookmarkIds());
        authorizationService.requireSameCollection(bookmarks, json.getCollectionId());
        bookmarkService.batchRemove(bookmarks);
    }
}
