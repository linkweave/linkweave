package org.linkweave.api.collection.favicon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import ch.dvbern.dvbstarter.clock.ClockProvider;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.bookmark.BookmarkService;
import org.linkweave.api.bookmark.BookmarkService.FaviconEvictionCandidate;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.shared.util.FileCacheCleanupUtil;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

/**
 * Implements UC-051: keeps the on-disk favicon cache below a configured size by
 * periodically evicting cache entries belonging to the oldest bookmarks.
 *
 * <p>Touches files only — never deletes or mutates any database row.
 */
@Service
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class FaviconCacheCleanupJobService {

    private final BookmarkService bookmarkService;
    private final FaviconCacheService cache;
    private final ClockProvider clockProvider;
    private final ConfigService configService;

    @Scheduled(
        cron = "{linkweave.favicon.cache-cleanup.cron:0 0 3 ? * SUN}",
        skipExecutionIf = DisabledPredicate.class,
        identity = "favicon-cache-cleanup"
    )
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void scheduledRun() {
        run();
    }

    /**
     * Visible for tests. Performs one cleanup pass and returns a summary of what happened.
     */
    @NonNull
    Result run() {
        return run(FileCacheCleanupUtil.parseSize(configService.getFaviconCacheCleanupMaxSize()),
            configService.getFaviconCacheCleanupMinBookmarkAge());
    }

    @NonNull
    Result run(long maxBytes, @NonNull Duration minAge) {
        Path dir = cache.getCacheDir();
        if (!Files.isDirectory(dir)) {
            LOG.info("Favicon cache directory {} does not exist; cleanup skipped", dir);
            return Result.ofSkipped();
        }

        long currentSize = FileCacheCleanupUtil.computeDirectorySize(dir, LOG);

        if (currentSize <= maxBytes) {
            LOG.info("Favicon cache size {} bytes within budget {} bytes; nothing to evict", currentSize, maxBytes);
            return Result.ofWithinBudget(currentSize);
        }

        Instant cutoff = Instant.now(clockProvider.getClock()).minus(minAge);
        Set<String> seenOrigins = new HashSet<>();
        long evictedFiles = 0L;
        long bytesFreed = 0L;
        Duration oldestEvictedAge = Duration.ZERO;

        for (FaviconEvictionCandidate candidate : bookmarkService.findFaviconEvictionCandidatesOldestFirst()) {
            OffsetDateTime created = candidate.createdAt();
            if (created != null && created.toInstant().isAfter(cutoff)) {
                // BR-109: every remaining bookmark is younger than the minimum age.
                break;
            }
            String origin = FaviconFetcherService.canonicalOrigin(candidate.url());
            if (!seenOrigins.add(origin)) {
                continue;
            }
            long freed = cache.deleteForOrigin(origin);
            if (freed > 0) {
                evictedFiles++;
                bytesFreed += freed;
                currentSize -= freed;
                if (created != null) {
                    Duration age = Duration.between(created.toInstant(), Instant.now(clockProvider.getClock()));
                    if (age.compareTo(oldestEvictedAge) > 0) {
                        oldestEvictedAge = age;
                    }
                }
                if (currentSize <= maxBytes) {
                    break;
                }
            }
        }

        if (currentSize > maxBytes) {
            LOG.warn(
                "Favicon cache cleanup exhausted eligible bookmarks but cache is still {} bytes (budget {} bytes). "
                    + "Evicted {} entries totalling {} bytes.",
                currentSize, maxBytes, evictedFiles, bytesFreed
            );
        } else {
            LOG.info(
                "Favicon cache cleanup evicted {} entries ({} bytes), final size {} bytes (budget {} bytes), oldest evicted age {}",
                evictedFiles, bytesFreed, currentSize, maxBytes, oldestEvictedAge
            );
        }
        return new Result(false, evictedFiles, bytesFreed, currentSize, oldestEvictedAge);
    }

    record Result(boolean skipped, long evictedFiles, long bytesFreed, long finalSize, @NonNull Duration oldestEvictedAge) {
        static @NonNull Result ofSkipped() {
            return new Result(true, 0L, 0L, 0L, Duration.ZERO);
        }
        static @NonNull Result ofWithinBudget(long size) {
            return new Result(false, 0L, 0L, size, Duration.ZERO);
        }
    }

    /** Skip predicate honoured by quarkus-scheduler when {@code linkweave.favicon.cache-cleanup.enabled=false}. */
    @ApplicationScoped
    @RequiredArgsConstructor
    public static class DisabledPredicate implements Scheduled.SkipPredicate {

        private final ConfigService configService;

        @Override
        public boolean test(io.quarkus.scheduler.ScheduledExecution execution) {
            return !configService.isFaviconCacheCleanupEnabled();
        }
    }
}
