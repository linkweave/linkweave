package org.chainlink.api.screenshot;

import java.net.URL;
import java.util.Optional;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.collection.favicon.BackendFetchPolicy;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class ScreenshotService {

    private final BookmarkRepo bookmarkRepo;
    private final ScreenshotCacheService cache;
    private final ScreenshotFetcherService fetcher;
    private final BackendFetchPolicy fetchPolicy;
    private final AppClock appClock;

    public @NonNull Optional<ScreenshotCacheService.CachedScreenshot> getScreenshot(@NonNull ID<Bookmark> bookmarkId) {
        return bookmarkRepo.findUrlById(bookmarkId)
            .map(ScreenshotCacheService::keyFor)
            .flatMap(cache::get)
            .filter(c -> !c.negative());
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
        var collection = bookmark.getCollection();
        if (fetchPolicy.blocks(url.getHost(), collection.getBrowserFetchAllowlist())) {
            // Host the backend can't reach (operator denylist / collection browser
            // allowlist): don't attempt and don't poison the cache with a negative
            // entry. Enforced here so it holds on both the scheduled job and the
            // user-initiated refresh path.
            return false;
        }
        Optional<ScreenshotFetcherService.FetchedScreenshot> fetched = fetcher.fetchFor(url);
        if (fetched.isPresent()) {
            cache.putSuccess(key, fetched.get().bytes(), fetched.get().contentType());
            bookmark.setScreenshotCapturedAt(appClock.offsetDateTime().now());
            return true;
        }
        cache.putNegative(key);
        return false;
    }

    /**
     * User-initiated refresh: drops the cached entry (success or negative) and
     * recaptures synchronously. Returns true if a fresh capture was stored.
     *
     * <p>Bypasses the scheduled job entirely so the user sees an updated image
     * as soon as the sidecar responds. On failure a negative-cache entry is
     * written (same as the job path), so subsequent reads return the fallback.
     */
    public boolean refreshScreenshot(@NonNull ID<Bookmark> bookmarkId) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());
        cache.deleteForKey(key);
        try {
            return captureNow(bookmarkId);
        } catch (Exception e) {
            cache.putNegative(key);
            return false;
        }
    }
}
