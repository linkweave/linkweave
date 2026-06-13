package org.chainlink.api.screenshot;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.collection.favicon.BackendFetchPolicy;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.NoTransactionService;
import org.jspecify.annotations.NonNull;

/**
 * The single producer of screenshot capture work. Each run processes at
 * most {@code batch-size} missing entries, letting backfills of large
 * collections (toggle-on) drain gradually instead of hammering the sidecar.
 *
 * <p>The job iterates bookmarks of screenshot-enabled collections newest-first,
 * asks the cache whether a fresh entry exists, and captures on misses until
 * the per-run budget is exhausted. Negative cache entries (default 12h TTL,
 * with exponential backoff) keep the job from re-trying URLs that just failed.
 *
 * <p>The DB filter ({@code screenshotCapturedAt}) is a coarse pre-filter, not a
 * permanent "done" flag: a bookmark re-enters the pending set once its capture
 * is older than the success TTL, so an expired (or evicted) cached image gets
 * regenerated rather than silently disappearing.
 *
 * <p>Non-transactional by design ({@link NoTransactionService}): the capture
 * loop makes blocking sidecar HTTP calls and delegates each DB write to
 * {@code captureNow}'s own short transaction. Running the loop inside a single
 * transaction (as a proxied caller — e.g. a test — would otherwise trigger via
 * the {@code @Service} stereotype) keeps a DB connection enlisted across the
 * REST calls and lets a rest-client thread stay associated at commit, which
 * Narayana aborts with "committing with N threads active".
 */
@NoTransactionService
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ScreenshotCaptureJobService {

    private final BookmarkRepo bookmarkRepo;
    private final ScreenshotCacheService cache;
    private final ScreenshotService screenshotService;
    private final BackendFetchPolicy fetchPolicy;
    private final ConfigService configService;

    @Scheduled(
        every = "{chainlink.screenshot.capture-job.every:30s}",
        skipExecutionIf = DisabledPredicate.class,
        identity = "screenshot-capture"
    )
    public void scheduledRun() {
        run();
    }

    @NonNull
    Result run() {
        return run(configService.getScreenshotCaptureJobBatchSize());
    }

    @NonNull
    Result run(int limitForRun) {
        if (limitForRun <= 0) {
            return new Result(0, 0, 0);
        }
        int captured = 0;
        int failed = 0;
        int scanned = 0;
        int offset = 0;
        outer:
        while (captured + failed < limitForRun) {
            var page = bookmarkRepo.findPendingScreenshotCaptures(
                limitForRun, offset, configService.getScreenshotSuccessTtl());
            if (page.isEmpty()) break;
            int capturedInPage = 0;
            for (BookmarkRepo.PendingScreenshotCapture b : page) {
                scanned++;
                String key = ScreenshotCacheService.keyFor(b.url());
                if (cache.get(key).isPresent()) {
                    continue; // negative cache still active; skip but keep budget free
                }
                if (shouldSkipCapture(b)) {
                    continue; // unreachable host (global skip list / collection allowlist) — keep budget free
                }
                boolean ok;
                try {
                    ok = screenshotService.captureNow(b.bookmarkId());
                } catch (Exception e) {
                    LOG.warn("Screenshot capture failed for bookmark {}: {}", b.bookmarkId(), e.getMessage());
                    failed++;
                    if (captured + failed >= limitForRun) break outer;
                    continue;
                }
                if (ok) { captured++; capturedInPage++; } else failed++;
                if (captured + failed >= limitForRun) break outer;
            }
            // Advance past the rows that are still pending (skipped + failed);
            // captured rows get a fresh screenshotCapturedAt and drop out of the
            // result set until they age past the success TTL again.
            offset += page.size() - capturedInPage;
            if (page.size() < limitForRun) break; // last page — no more pending captures
        }
        if (captured + failed > 0) {
            LOG.info("Screenshot capture tick: scanned={} captured={} failed={} budget={}",
                scanned, captured, failed, limitForRun);
        }
        return new Result(captured, failed, scanned);
    }

    /**
     * Pre-filter over {@link BackendFetchPolicy}: skip hosts the backend cannot
     * reach <em>before</em> calling {@code captureNow}, so they neither consume
     * capture budget nor load the bookmark entity. {@code captureNow} applies the
     * same policy again as the safety net for non-job callers (e.g. the refresh
     * endpoint).
     */
    private boolean shouldSkipCapture(BookmarkRepo.@NonNull PendingScreenshotCapture candidate) {
        return fetchPolicy.blocks(candidate.url().getHost(), candidate.collectionBrowserAllowlist());
    }

    record Result(int captured, int failed, int scanned) {}

    @ApplicationScoped
    @RequiredArgsConstructor
    public static class DisabledPredicate implements Scheduled.SkipPredicate {

        private final ConfigService configService;

        @Override
        public boolean test(ScheduledExecution execution) {
            return !configService.isScreenshotEnabled() || !configService.isScreenshotCaptureJobEnabled();
        }
    }
}
