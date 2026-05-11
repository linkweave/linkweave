package org.chainlink.api.collection.favicon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
class FaviconCacheCleanupJobServiceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    FaviconCacheService cacheService;

    @Inject
    FaviconCacheCleanupJobService cleanupJob;

    @Inject
    BookmarkRepo bookmarkRepo;

    @Inject
    EntityManager em;

    @BeforeEach
    void clearCacheDir() throws IOException {
        Path dir = cacheService.getCacheDir();
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) { /* best-effort */ }
            });
        }
    }

    @Test
    void shouldDoNothing_whenCacheBelowThreshold() throws IOException {
        Bookmark bookmark = fixtureService.createTestBookmark(b -> b.withUrl("https://example-below.test"));
        backdateCreated(bookmark, 60);
        String origin = FaviconFetcherService.canonicalOrigin(bookmark.getUrl());
        cacheService.putSuccess(origin, bytes(64), "image/png");

        FaviconCacheCleanupJobService.Result result = cleanupJob.run(1024L * 1024L, Duration.ofDays(28));

        assertThat(result.evictedFiles()).isZero();
        assertThat(payloadFile(origin)).exists();
    }

    @Test
    void shouldEvictOldestFirst_untilThresholdMet() throws IOException {
        Bookmark older = fixtureService.createTestBookmark(b -> b.withUrl("https://older.test"));
        Bookmark newer = fixtureService.createTestBookmark(b -> b.withUrl("https://newer.test"));
        backdateCreated(older, 100);
        backdateCreated(newer, 60);

        String oldOrigin = FaviconFetcherService.canonicalOrigin(older.getUrl());
        String newOrigin = FaviconFetcherService.canonicalOrigin(newer.getUrl());
        cacheService.putSuccess(oldOrigin, bytes(2048), "image/png");
        cacheService.putSuccess(newOrigin, bytes(2048), "image/png");

        // Budget is 3000 bytes; cache holds 4096 + 4096 (and metadata sidecars); after evicting
        // the older entry the size drops below the threshold so iteration stops.
        FaviconCacheCleanupJobService.Result result = cleanupJob.run(3000L, Duration.ofDays(28));

        assertThat(result.evictedFiles()).isEqualTo(1);
        assertThat(payloadFile(oldOrigin)).doesNotExist();
        assertThat(payloadFile(newOrigin)).exists();
    }

    @Test
    void shouldNotEvict_whenAllBookmarksYoungerThanMinAge() throws IOException {
        Bookmark fresh = fixtureService.createTestBookmark(b -> b.withUrl("https://fresh.test"));
        // leave timestampErstellt at default (today)
        String origin = FaviconFetcherService.canonicalOrigin(fresh.getUrl());
        cacheService.putSuccess(origin, bytes(8192), "image/png");

        FaviconCacheCleanupJobService.Result result = cleanupJob.run(1L, Duration.ofDays(28));

        assertThat(result.evictedFiles()).isZero();
        assertThat(payloadFile(origin)).exists();
    }

    @Test
    void shouldHandleEmptyCacheGracefully() {
        FaviconCacheCleanupJobService.Result result = cleanupJob.run(1L, Duration.ofDays(28));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldParseSizeStrings() {
        assertThat(FaviconCacheCleanupJobService.parseSize("40MB")).isEqualTo(40L * 1024 * 1024);
        assertThat(FaviconCacheCleanupJobService.parseSize("1G")).isEqualTo(1024L * 1024 * 1024);
        assertThat(FaviconCacheCleanupJobService.parseSize("512")).isEqualTo(512L);
        assertThat(FaviconCacheCleanupJobService.parseSize("2KB")).isEqualTo(2048L);
    }

    void backdateCreated(Bookmark bookmark, int daysAgo) {
        OffsetDateTime ts = OffsetDateTime.now().minusDays(daysAgo);
        QuarkusTransaction.requiringNew().run(() ->
            em.createQuery("update Bookmark b set b.timestampErstellt = :ts where b.id = :id")
                .setParameter("ts", ts)
                .setParameter("id", bookmark.getId().getUUID())
                .executeUpdate()
        );
    }

    private Path payloadFile(String origin) {
        return cacheService.getCacheDir().resolve(FaviconCacheService.sha256(origin) + ".bin");
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i & 0xff);
        }
        return b;
    }
}
