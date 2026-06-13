package org.chainlink.api.cleanup;

import java.util.List;
import java.util.Objects;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.cleanup.json.CleanupSuggestionJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class CleanupSuggestionService {

    private final BookmarkRepo bookmarkRepo;
    private final ConfigService configService;
    private final AppClock appClock;

    @NonNull
    public List<CleanupSuggestionJson> getSuggestions(
        @NonNull ID<Collection> collectionId,
        int thresholdMonths
    ) {
        return bookmarkRepo.findStaleByCollection(collectionId, thresholdMonths).stream()
            .map(this::toSuggestionJson)
            .toList();
    }

    @NonNull
    public List<Integer> getAvailableThresholds() {
        return configService.getCleanupAvailableThresholds();
    }

    public int getDefaultThreshold() {
        return configService.getCleanupDefaultThresholdMonths();
    }

    public boolean isValidThreshold(int months) {
        return getAvailableThresholds().contains(months);
    }

    public void dismissSuggestion(@NonNull ID<Bookmark> bookmarkId) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        bookmark.setSuggestionDismissedAt(appClock.offsetDateTime().now());
        bookmarkRepo.persist(bookmark);
    }

    @NonNull
    private CleanupSuggestionJson toSuggestionJson(@NonNull Bookmark bookmark) {
        boolean neverClicked = bookmark.getClickCount() == 0 && bookmark.getLastClickedAt() == null;
        return new CleanupSuggestionJson(
            bookmark.getId(),
            bookmark.getTitle(),
            bookmark.getUrl().toString(),
            bookmark.getFolder() != null ? Objects.requireNonNull(bookmark.getFolder()).getName() : null,
            bookmark.getClickCount(),
            bookmark.getLastClickedAt(),
            bookmark.getTimestampErstellt(),
            neverClicked
        );
    }
}
