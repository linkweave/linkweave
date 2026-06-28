package org.linkweave.api.screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import org.linkweave.infrastructure.clock.ClockProvider;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.shared.Util;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.shared.util.FileCacheCleanupUtil;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ScreenshotCacheCleanupJobService {

    private final BookmarkRepo bookmarkRepo;
    private final ScreenshotCacheService cache;
    private final ClockProvider clockProvider;
    private final ConfigService configService;

    @Scheduled(
        cron = "{linkweave.screenshot.cache-cleanup.cron:0 0 3 ? * SUN}",
        skipExecutionIf = DisabledPredicate.class,
        identity = "screenshot-cache-cleanup"
    )
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void scheduledRun() {
        run();
    }

    @NonNull
    Result run() {
        return run(FileCacheCleanupUtil.parseSize(configService.getScreenshotCacheCleanupMaxSize()),
            configService.getScreenshotCacheCleanupMinBookmarkAge());
    }

    @NonNull
    Result run(long maxBytes, @NonNull Duration minAge) {
        Path dir = cache.getCacheDir();
        if (!Files.isDirectory(dir)) {
            LOG.info("Screenshot cache directory {} does not exist; cleanup skipped", dir);
            return Result.ofSkipped();
        }

        long currentSize = FileCacheCleanupUtil.computeDirectorySize(dir, LOG);
        if (currentSize <= maxBytes) {
            LOG.info("Screenshot cache size {} bytes within budget {} bytes; nothing to evict", currentSize, maxBytes);
            return Result.ofWithinBudget(currentSize);
        }

        Instant cutoff = Instant.now(clockProvider.getClock()).minus(minAge);
        Set<String> seen = new HashSet<>();
        long evicted = 0L;
        long bytesFreed = 0L;

        for (Bookmark b : bookmarkRepo.findAllOldestFirstNotDeleted()) {
            OffsetDateTime ts = Util.coalesce(b.getLastClickedAt(), b.getTimestampErstellt()).orElse(null);
            if (ts != null && ts.toInstant().isAfter(cutoff)) {
                break;
            }
            String key = ScreenshotCacheService.keyFor(b.getUrl());
            if (!seen.add(key)) continue;
            long freed = cache.deleteForKey(key);
            if (freed > 0) {
                evicted++;
                bytesFreed += freed;
                currentSize -= freed;
                if (currentSize <= maxBytes) break;
            }
        }

        if (currentSize > maxBytes) {
            LOG.warn("Screenshot cache cleanup exhausted eligible bookmarks but cache is still {} bytes (budget {} bytes).",
                currentSize, maxBytes);
        } else {
            LOG.info("Screenshot cache cleanup evicted {} entries ({} bytes), final size {} bytes (budget {} bytes)",
                evicted, bytesFreed, currentSize, maxBytes);
        }
        return new Result(false, evicted, bytesFreed, currentSize);
    }

    record Result(boolean skipped, long evictedFiles, long bytesFreed, long finalSize) {
        static @NonNull Result ofSkipped() {
            return new Result(true, 0L, 0L, 0L);
        }
        static @NonNull Result ofWithinBudget(long size) {
            return new Result(false, 0L, 0L, size);
        }
    }

    @ApplicationScoped
    @RequiredArgsConstructor
    public static class DisabledPredicate implements Scheduled.SkipPredicate {

        private final ConfigService configService;

        @Override
        public boolean test(io.quarkus.scheduler.ScheduledExecution execution) {
            return !configService.isScreenshotCacheCleanupEnabled();
        }
    }
}
