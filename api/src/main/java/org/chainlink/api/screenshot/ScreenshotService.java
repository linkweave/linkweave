package org.chainlink.api.screenshot;

import java.net.URL;
import java.util.Optional;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class ScreenshotService {

    private final BookmarkRepo bookmarkRepo;
    private final ScreenshotCacheService cache;
    private final ScreenshotFetcherService fetcher;
    private final AppClock appClock;

    public @NonNull Optional<ScreenshotCacheService.CachedScreenshot> getScreenshot(@NonNull ID<Bookmark> bookmarkId) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());
        return cache.get(key).filter(c -> !c.negative());
    }

    /**
     * Captures the bookmark's URL via the sidecar, writes a cache entry, and
     * stamps {@code screenshotCapturedAt} on the bookmark when successful.
     * Returns true if a successful capture was stored, false otherwise.
     *
     * <p>Intended to be invoked by the scheduled capture job only.
     */
    public boolean captureNow(@NonNull ID<Bookmark> bookmarkId) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        URL url = bookmark.getUrl();
        String key = ScreenshotCacheService.keyFor(url);
        Optional<ScreenshotFetcherService.FetchedScreenshot> fetched = fetcher.fetchFor(url);
        if (fetched.isPresent()) {
            cache.putSuccess(key, fetched.get().bytes(), fetched.get().contentType());
            bookmark.setScreenshotCapturedAt(appClock.offsetDateTime().now());
            return true;
        }
        cache.putNegative(key);
        return false;
    }
}
