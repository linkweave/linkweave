package org.chainlink.api.screenshot;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
class ScreenshotServiceITest {

    @Inject
    ScreenshotService screenshotService;

    @Inject
    ScreenshotCacheService cache;

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldNotCaptureNorNegativelyCacheOnRefreshWhenHostMatchesAllowlist() {
        // The user-initiated refresh path must honour the collection allowlist,
        // not just the scheduled job — otherwise "refresh" on an intranet host
        // would write the negative entry the allowlist is meant to prevent.
        Collection allowlisted = fixtureService.createTestCollection(b -> b
            .withScreenshotEnabled(true)
            .withFaviconAllowlist("intranet.local"));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(allowlisted)
            .withUrl("https://intranet.local/page-" + UUID.randomUUID()));
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        boolean refreshed = screenshotService.refreshScreenshot(bookmark.getId());

        Assertions.assertThat(refreshed)
            .as("allowlisted host cannot be captured by the backend")
            .isFalse();
        Assertions.assertThat(cache.get(key))
            .as("refresh of an allowlisted host must not write a negative cache entry")
            .isEmpty();
    }

    @Test
    void shouldNotNegativelyCacheOnCaptureWhenHostMatchesAllowlist() {
        Collection allowlisted = fixtureService.createTestCollection(b -> b
            .withScreenshotEnabled(true)
            .withFaviconAllowlist("*.corp.internal"));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(allowlisted)
            .withUrl("https://wiki.corp.internal/x-" + UUID.randomUUID()));
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());

        boolean captured = screenshotService.captureNow(bookmark.getId());

        Assertions.assertThat(captured).isFalse();
        Assertions.assertThat(cache.get(key)).isEmpty();
    }
}
