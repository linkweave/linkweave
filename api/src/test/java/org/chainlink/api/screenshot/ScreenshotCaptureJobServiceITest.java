package org.chainlink.api.screenshot;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link ScreenshotCaptureJobService#run(int)} directly so we can
 * assert per-tick selection logic without waiting on the @Scheduled cadence
 * or standing up the real sidecar. The sidecar is unreachable in tests, so
 * any "captured" attempt resolves to a negative cache entry — exactly the
 * signal we use to confirm the job *tried* to capture a given URL.
 *
 * <p>Test methods don't reset the DB between runs, so each assertion targets
 * the specific bookmark that test created (via a unique URL) rather than
 * totals.
 */
@QuarkusTest
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
class ScreenshotCaptureJobServiceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    ScreenshotCaptureJobService job;

    @Inject
    ScreenshotCacheService cache;

    @Inject
    BookmarkRepo bookmarkRepo;

    @Inject
    ConfigService configService;

    @Test
    void shouldSkipBookmarksFromCollectionsWithScreenshotsDisabled() {
        Collection disabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(false));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(disabled)
            .withUrl("https://example.com/skip-" + UUID.randomUUID())
        );
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        job.run(50);

        // The disabled-collection bookmark must never have been touched. Other
        // tests may have left enabled-collection bookmarks behind; we don't
        // care about those — only that *this* URL got no cache entry.
        Assertions.assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void shouldRespectBatchBudget() {
        Collection enabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(true));
        for (int i = 0; i < 5; i++) {
            fixtureService.persistBookmark(builder -> builder
                .withCollection(enabled)
                .withUrl("https://example.com/budget-" + UUID.randomUUID())
            );
        }

        var result = job.run(2);

        // Capture loop must stop at the budget regardless of how many cache
        // misses remain.
        Assertions.assertThat(result.captured() + result.failed()).isEqualTo(2);
    }

    @Test
    void shouldSkipBookmarksWithFreshCacheEntry() {
        Collection enabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(true));
        Bookmark cached = fixtureService.persistBookmark(builder -> builder
            .withCollection(enabled)
            .withUrl("https://example.com/already-cached-" + UUID.randomUUID())
        );
        String key = ScreenshotCacheService.keyFor(cached.getUrl());
        byte[] sentinel = new byte[]{1, 2, 3};
        cache.putSuccess(key, sentinel, "image/jpeg");

        try {
            job.run(50);

            // If the job re-fetched, the cache entry would now be a negative
            // entry (sidecar unreachable in tests). A fresh cache hit must be
            // preserved unchanged.
            var loaded = cache.get(key);
            Assertions.assertThat(loaded).isPresent();
            Assertions.assertThat(loaded.get().negative()).isFalse();
            Assertions.assertThat(loaded.get().bytes()).isEqualTo(sentinel);
        } finally {
            cache.deleteForKey(key);
        }
    }

    @Test
    void shouldNoOpWithZeroBudget() {
        Collection enabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(true));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(enabled)
            .withUrl("https://example.com/zero-budget-" + UUID.randomUUID())
        );
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        var result = job.run(0);

        Assertions.assertThat(result.captured()).isZero();
        Assertions.assertThat(result.scanned()).isZero();
        Assertions.assertThat(cache.get(key)).isEmpty();
    }

    /**
     * Seals every currently-pending bookmark into the negative cache so that
     * only the bookmark created by the caller is uncached. This lets subsequent
     * assertions about the target be made in isolation despite the shared DB.
     */
    private void blockAllExistingPendingBookmarks() {
        // Enumerate exactly what the job would consider (same recapture threshold)
        // so only the bookmark created by the caller is left uncached.
        bookmarkRepo.findPendingScreenshotCaptures(
                Integer.MAX_VALUE, 0, configService.getScreenshotSuccessTtl())
            .forEach(c -> cache.putNegative(ScreenshotCacheService.keyFor(c.url())));
    }

    @Test
    void shouldPagePastAFullyBlockedPageToReachWork() {
        // Block every pre-existing pending bookmark so only our target is uncached.
        blockAllExistingPendingBookmarks();

        Collection enabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(true));

        // Target: push its timestamp far into the past so the blocked bookmarks,
        // created immediately after, sort ahead of it in newest-modified-first order.
        Bookmark target = fixtureService.persistBookmark(builder -> builder
            .withCollection(enabled)
            .withUrl("https://example.com/pagination-target-" + UUID.randomUUID())
        );
        fixtureService.setTimestampErstellt(target, OffsetDateTime.now().minusYears(10));
        String targetKey = ScreenshotCacheService.keyFor(target.getUrl());

        // Three blocked bookmarks are newer → they fill page 1 entirely.
        int pageSize = 3;
        for (int i = 0; i < pageSize; i++) {
            Bookmark blocked = fixtureService.persistBookmark(builder -> builder
                .withCollection(enabled)
                .withUrl("https://example.com/pagination-blocked-" + UUID.randomUUID())
            );
            cache.putNegative(ScreenshotCacheService.keyFor(blocked.getUrl()));
        }

        // Run with limit == pageSize: without pagination the job would load the 3 blocked
        // bookmarks, skip all of them, and stop — never reaching the target.
        job.run(pageSize);

        Assertions.assertThat(cache.get(targetKey))
            .as("job must page past the fully-blocked first page to reach the target")
            .isPresent();
    }

    @Test
    void shouldStopOnEmptyPage() {
        // Block everything pending so the very next page query returns nothing.
        blockAllExistingPendingBookmarks();

        var result = job.run(10);

        Assertions.assertThat(result.captured()).isZero();
        Assertions.assertThat(result.failed()).isZero();
    }

    @Test
    void shouldStopOnPartialPage() {
        blockAllExistingPendingBookmarks();

        Collection enabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(true));
        // Only 2 uncached bookmarks exist; budget is 5 → the page is partial (2 < 5)
        // and the loop must stop rather than spinning on further empty pages.
        for (int i = 0; i < 2; i++) {
            fixtureService.persistBookmark(builder -> builder
                .withCollection(enabled)
                .withUrl("https://example.com/partial-page-" + UUID.randomUUID())
            );
        }

        var result = job.run(5);

        // Both bookmarks were attempted (sidecar unreachable → failed) and the loop
        // stopped without spinning — captured + failed == 2, not 5.
        Assertions.assertThat(result.captured() + result.failed()).isEqualTo(2);
    }

    @Test
    void shouldSkipBookmarkWhoseHostMatchesCollectionAllowlist() {
        // Host is in the collection's favicon allowlist → the backend can't reach
        // it (the browser loads it directly). The job must not attempt capture and
        // must not leave a negative cache entry that would re-trigger every TTL.
        Collection allowlisted = fixtureService.createTestCollection(b -> b
            .withScreenshotEnabled(true)
            .withBrowserFetchAllowlist("intranet.local"));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(allowlisted)
            .withUrl("https://intranet.local/page-" + UUID.randomUUID()));
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        job.run(50);

        Assertions.assertThat(cache.get(key))
            .as("allowlisted host must not be captured nor negatively cached")
            .isEmpty();
    }

    @Test
    void shouldStillCaptureBookmarkWhoseHostIsNotInCollectionAllowlist() {
        // Same collection has an allowlist, but this bookmark's host is not in it
        // → the job proceeds and a cache entry appears (success or negative
        // depending on sidecar availability), which is proof a capture was tried.
        Collection withList = fixtureService.createTestCollection(b -> b
            .withScreenshotEnabled(true)
            .withBrowserFetchAllowlist("intranet.local"));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(withList)
            .withUrl("https://example.com/not-listed-" + UUID.randomUUID()));
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        job.run(50);

        Assertions.assertThat(cache.get(key))
            .as("non-allowlisted host must still be attempted")
            .isPresent();
    }

    @Test
    void shouldRecaptureWhenPreviousCaptureIsOlderThanSuccessTtl() {
        blockAllExistingPendingBookmarks();

        Collection enabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(true));
        // Captured long enough ago that its cached image would have expired → the
        // bookmark must re-enter the pending set and be attempted again.
        OffsetDateTime stale = OffsetDateTime.now()
            .minus(configService.getScreenshotSuccessTtl())
            .minusDays(1);
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(enabled)
            .withUrl("https://example.com/stale-capture-" + UUID.randomUUID())
            .withScreenshotCapturedAt(stale));
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        job.run(50);

        Assertions.assertThat(cache.get(key))
            .as("a capture older than the success TTL must be re-attempted")
            .isPresent();
    }

    @Test
    void shouldSkipBookmarksWithNonNullScreenshotCapturedAt() {
        Collection enabled = fixtureService.createTestCollection(b -> b.withScreenshotEnabled(true));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(enabled)
            .withUrl("https://example.com/already-captured-" + UUID.randomUUID())
            .withScreenshotCapturedAt(OffsetDateTime.now().minusHours(1))
        );
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        job.run(50);

        // The DB query filters this bookmark out — no cache entry means no attempt was made.
        Assertions.assertThat(cache.get(key)).isEmpty();
    }
}
