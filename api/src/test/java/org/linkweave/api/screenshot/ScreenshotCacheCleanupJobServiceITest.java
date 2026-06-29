package org.linkweave.api.screenshot;

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
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.shared.cache.SidecarFileCache;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
class ScreenshotCacheCleanupJobServiceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    ScreenshotCacheService cacheService;

    @Inject
    ScreenshotCacheCleanupJobService cleanupJob;

    @Inject
    EntityManager em;

    @BeforeEach
    void clearCacheDir() throws IOException {
        Path dir = cacheService.getCacheDir();
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    void shouldDoNothing_whenCacheBelowThreshold() {
        // ARRANGE
        Bookmark bookmark = fixtureService.createTestBookmark(b -> b.withUrl("https://example-below.test"));
        backdateCreated(bookmark, 60);
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());
        cacheService.putSuccess(key, bytes(64), "image/jpeg");

        // ACT
        ScreenshotCacheCleanupJobService.Result result = cleanupJob.run(1024L * 1024L, Duration.ofDays(28));

        // ASSERT
        assertThat(result.evictedFiles()).isZero();
        assertThat(payloadFile(key)).exists();
    }

    @Test
    void shouldEvictOldestFirst_untilThresholdMet() {
        // ARRANGE
        Bookmark older = fixtureService.createTestBookmark(b -> b.withUrl("https://older.test"));
        Bookmark newer = fixtureService.createTestBookmark(b -> b.withUrl("https://newer.test"));
        backdateCreated(older, 100);
        backdateCreated(newer, 60);

        String oldKey = ScreenshotCacheService.keyFor(older.getUrl());
        String newKey = ScreenshotCacheService.keyFor(newer.getUrl());
        cacheService.putSuccess(oldKey, bytes(2048), "image/jpeg");
        cacheService.putSuccess(newKey, bytes(2048), "image/jpeg");

        // ACT
        ScreenshotCacheCleanupJobService.Result result = cleanupJob.run(3000L, Duration.ofDays(28));

        // ASSERT
        assertThat(result.evictedFiles()).isEqualTo(1);
        assertThat(payloadFile(oldKey)).doesNotExist();
        assertThat(payloadFile(newKey)).exists();
    }

    @Test
    void shouldNotEvict_whenAllBookmarksYoungerThanMinAge() {
        // ARRANGE
        Bookmark fresh = fixtureService.createTestBookmark(b -> b.withUrl("https://fresh.test"));
        String key = ScreenshotCacheService.keyFor(fresh.getUrl());
        cacheService.putSuccess(key, bytes(8192), "image/jpeg");

        // ACT
        ScreenshotCacheCleanupJobService.Result result = cleanupJob.run(1L, Duration.ofDays(28));

        // ASSERT
        assertThat(result.evictedFiles()).isZero();
        assertThat(payloadFile(key)).exists();
    }

    @Test
    void shouldHandleEmptyCacheGracefully() {
        ScreenshotCacheCleanupJobService.Result result = cleanupJob.run(1L, Duration.ofDays(28));
        assertThat(result).isNotNull();
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

    private Path payloadFile(String key) {
        return cacheService.getCacheDir().resolve(SidecarFileCache.sha256(key) + ".bin");
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i & 0xff);
        }
        return b;
    }
}
