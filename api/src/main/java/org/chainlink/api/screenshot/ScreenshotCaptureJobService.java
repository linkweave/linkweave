package org.chainlink.api.screenshot;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

/**
 * The single producer of screenshot capture work. Each run processes at
 * most {@code batch-size} missing entries, letting backfills of large
 * collections (toggle-on) drain gradually instead of hammering the sidecar.
 *
 * <p>State is cache-only — there is no per-bookmark "needs capture" column.
 * The job iterates bookmarks of screenshot-enabled collections newest-first,
 * asks the cache whether a fresh entry exists, and captures on misses until
 * the per-run budget is exhausted. Negative cache entries (default 12h TTL)
 * keep the job from re-trying URLs that just failed.
 */
@Service
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ScreenshotCaptureJobService {

    private final BookmarkRepo bookmarkRepo;
    private final ScreenshotCacheService cache;
    private final ScreenshotService screenshotService;
    private final ConfigService configService;

    @Scheduled(
        every = "{chainlink.screenshot.capture-job.every:30s}",
        skipExecutionIf = DisabledPredicate.class,
        identity = "screenshot-capture"
    )
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
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
            var page = bookmarkRepo.findPendingScreenshotCaptures(limitForRun, offset);
            if (page.isEmpty()) break;
            int capturedInPage = 0;
            for (Bookmark b : page) {
                scanned++;
                String key = ScreenshotCacheService.keyFor(b.getUrl());
                if (cache.get(key).isPresent()) {
                    continue; // negative cache still active; skip but keep budget free
                }
                boolean ok = screenshotService.captureNow(b.getId());
                if (ok) { captured++; capturedInPage++; } else failed++;
                if (captured + failed >= limitForRun) break outer;
            }
            // Advance past the rows that are still pending (skipped + failed);
            // captured rows have screenshotCapturedAt set and will leave the result set.
            offset += page.size() - capturedInPage;
            if (page.size() < limitForRun) break; // last page — no more pending captures
        }
        if (captured + failed > 0) {
            LOG.info("Screenshot capture tick: scanned={} captured={} failed={} budget={}",
                scanned, captured, failed, limitForRun);
        }
        return new Result(captured, failed, scanned);
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
