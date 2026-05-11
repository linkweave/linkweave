package org.chainlink.api.collection.favicon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import ch.dvbern.dvbstarter.clock.ClockProvider;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.bookmark.BookmarkService;
import org.chainlink.api.bookmark.BookmarkService.FaviconEvictionCandidate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.NonNull;

/**
 * Implements UC-051: keeps the on-disk favicon cache below a configured size by
 * periodically evicting cache entries belonging to the oldest bookmarks.
 *
 * <p>Touches files only — never deletes or mutates any database row.
 */
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class FaviconCacheCleanupJob {

    private final BookmarkService bookmarkService;
    private final FaviconCacheService cache;
    private final ClockProvider clockProvider;

    @ConfigProperty(name = "chainlink.favicon.cache-cleanup.max-size", defaultValue = "40MB")
    String maxSizeRaw;

    @ConfigProperty(name = "chainlink.favicon.cache-cleanup.min-bookmark-age", defaultValue = "28D")
    Duration minBookmarkAge;

    @Scheduled(
        cron = "{chainlink.favicon.cache-cleanup.cron:0 0 3 ? * SUN}",
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
        return run(parseSize(maxSizeRaw), minBookmarkAge);
    }

    @NonNull
    Result run(long maxBytes, @NonNull Duration minAge) {
        Path dir = cache.getCacheDir();
        if (!Files.isDirectory(dir)) {
            LOG.info("Favicon cache directory {} does not exist; cleanup skipped", dir);
            return Result.ofSkipped();
        }

        long currentSize = computeDirectorySize(dir);

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

    private static long computeDirectorySize(@NonNull Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    LOG.warn("Failed to stat favicon cache file {}: {}", p, e.getMessage());
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            LOG.warn("Failed to list favicon cache directory {}: {}", dir, e.getMessage());
            return 0L;
        }
    }

    /**
     * Parses sizes such as {@code "40MB"}, {@code "100K"}, {@code "1G"} or a bare byte
     * count. Spaces and case are tolerated.
     */
    static long parseSize(@NonNull String raw) {
        String s = raw.trim().toUpperCase();
        long mult = 1L;
        if (s.endsWith("KB") || s.endsWith("K")) {
            mult = 1024L;
            s = s.substring(0, s.length() - (s.endsWith("KB") ? 2 : 1));
        } else if (s.endsWith("MB") || s.endsWith("M")) {
            mult = 1024L * 1024L;
            s = s.substring(0, s.length() - (s.endsWith("MB") ? 2 : 1));
        } else if (s.endsWith("GB") || s.endsWith("G")) {
            mult = 1024L * 1024L * 1024L;
            s = s.substring(0, s.length() - (s.endsWith("GB") ? 2 : 1));
        } else if (s.endsWith("B")) {
            s = s.substring(0, s.length() - 1);
        }
        return Long.parseLong(s.trim()) * mult;
    }

    record Result(boolean skipped, long evictedFiles, long bytesFreed, long finalSize, @NonNull Duration oldestEvictedAge) {
        static @NonNull Result ofSkipped() {
            return new Result(true, 0L, 0L, 0L, Duration.ZERO);
        }
        static @NonNull Result ofWithinBudget(long size) {
            return new Result(false, 0L, 0L, size, Duration.ZERO);
        }
    }

    /** Skip predicate honoured by quarkus-scheduler when {@code chainlink.favicon.cache-cleanup.enabled=false}. */
    @ApplicationScoped
    public static class DisabledPredicate implements Scheduled.SkipPredicate {

        @ConfigProperty(name = "chainlink.favicon.cache-cleanup.enabled", defaultValue = "true")
        boolean enabled;

        @Override
        public boolean test(io.quarkus.scheduler.ScheduledExecution execution) {
            return !enabled;
        }
    }
}
