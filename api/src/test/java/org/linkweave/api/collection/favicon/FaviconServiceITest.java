package org.linkweave.api.collection.favicon;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
class FaviconServiceITest {

    @Inject
    FaviconService faviconService;

    @Inject
    FaviconCacheService cache;

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldSuppressFetchAndNotCacheWhenHostMatchesCollectionAllowlist() {
        Collection allowlisted = fixtureService.createTestCollection(b -> b
            .withBrowserFetchAllowlist("intranet.local"));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(allowlisted)
            .withUrl("https://intranet.local/page-" + UUID.randomUUID()));
        String origin = FaviconFetcherService.canonicalOrigin(bookmark.getUrl());

        Assertions.assertThat(faviconService.getFavicon(bookmark.getId()))
            .as("allowlisted host is loaded by the browser; backend returns nothing")
            .isEmpty();
        Assertions.assertThat(cache.get(origin))
            .as("no server-side fetch attempted → no (negative) cache entry written")
            .isEmpty();
    }

    @Test
    void shouldMatchAllowlistWildcardSubdomain() {
        Collection allowlisted = fixtureService.createTestCollection(b -> b
            .withBrowserFetchAllowlist("*.mycompany.domain"));
        Bookmark bookmark = fixtureService.persistBookmark(builder -> builder
            .withCollection(allowlisted)
            .withUrl("https://wiki.mycompany.domain/x-" + UUID.randomUUID()));
        String origin = FaviconFetcherService.canonicalOrigin(bookmark.getUrl());

        Assertions.assertThat(faviconService.getFavicon(bookmark.getId())).isEmpty();
        Assertions.assertThat(cache.get(origin)).isEmpty();
    }
}
