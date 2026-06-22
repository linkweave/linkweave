package org.linkweave.api.screenshot;

import java.net.URL;
import java.util.Optional;

import ch.dvbern.dvbstarter.clock.AppClock;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.collection.favicon.BackendFetchPolicy;
import org.linkweave.infrastructure.stereotypes.NoTransactionService;
import org.jspecify.annotations.NonNull;

/**
 * Orchestrates screenshot capture. Runs <em>without</em> a transaction
 * ({@link NoTransactionService}) so the blocking sidecar call never holds a
 * pooled DB connection across its network round-trip. The DB write is delegated
 * to {@link ScreenshotWriteService}, which commits it in a short transaction as
 * the system admin — the scheduled job has no logged-in user, so the entity
 * audit listener (which stamps {@code userMutiert}) would otherwise throw on the
 * anonymous thread and silently roll the stamp back.
 */
@NoTransactionService
@RequiredArgsConstructor
@Slf4j
public class ScreenshotService {

    private final BookmarkRepo bookmarkRepo;
    private final ScreenshotCacheService cache;
    private final ScreenshotFetcherService fetcher;
    private final ScreenshotWriteService writer;
    private final BackendFetchPolicy fetchPolicy;
    private final AppClock appClock;

    public @NonNull Optional<ScreenshotCacheService.CachedScreenshot> getScreenshot(@NonNull ID<Bookmark> bookmarkId) {
        return bookmarkRepo.findUrlById(bookmarkId)
            .map(ScreenshotCacheService::keyFor)
            .flatMap(cache::get)
            .filter(c -> !c.negative());
    }

    /**
     * Captures the bookmark's URL via the sidecar, writes a cache entry, and —
     * via {@link ScreenshotWriteService} — stamps {@code screenshotCapturedAt}
     * (plus an opportunistic description backfill) when successful. Returns true
     * if a successful capture was stored, false otherwise.
     */
    public boolean captureNow(@NonNull ID<Bookmark> bookmarkId) {
        BookmarkRepo.UrlFetchContext ctx = bookmarkRepo.findUrlFetchContextById(bookmarkId)
            .orElseGet(() -> new BookmarkRepo.UrlFetchContext(
                bookmarkRepo.getById(bookmarkId).getUrl(), null)); // throws the canonical not-found
        URL url = ctx.url();
        String key = ScreenshotCacheService.keyFor(url);
        if (fetchPolicy.blocks(url.getHost(), ctx.collectionBrowserAllowlist())) {
            // Host the backend can't reach (operator denylist / collection browser
            // allowlist): don't attempt and don't poison the cache with a negative
            // entry. Enforced here so it holds on both the scheduled job and the
            // user-initiated refresh path.
            return false;
        }
        Optional<ScreenshotFetcherService.FetchedScreenshot> fetched = fetcher.fetchFor(url);
        if (fetched.isPresent()) {
            cache.putSuccess(key, fetched.get().bytes(), fetched.get().contentType());
            writer.applyCapture(bookmarkId, appClock.offsetDateTime().now(), fetched.get().description());
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
        URL url = bookmarkRepo.findUrlById(bookmarkId)
            .orElseGet(() -> bookmarkRepo.getById(bookmarkId).getUrl());
        String key = ScreenshotCacheService.keyFor(url);
        cache.deleteForKey(key);
        try {
            return captureNow(bookmarkId);
        } catch (Exception e) {
            // Root cause only — a full stack trace here would be noise for what
            // is almost always an unreachable host or a sidecar hiccup.
            LOG.warn("Screenshot refresh failed for bookmark {}: {}",
                bookmarkId, ExceptionUtils.getRootCauseMessage(e));
            cache.putNegative(key);
            return false;
        }
    }
}
