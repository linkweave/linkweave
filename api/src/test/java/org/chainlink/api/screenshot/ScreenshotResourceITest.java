package org.chainlink.api.screenshot;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ScreenshotResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    ScreenshotCacheService cacheService;

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        RestAssured.given()
            .pathParam("collectionId", UUID.randomUUID().toString())
            .pathParam("bookmarkId", UUID.randomUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenCollectionAccessDenied() {
        RestAssured.given()
            .pathParam("collectionId", UUID.randomUUID().toString())
            .pathParam("bookmarkId", UUID.randomUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn204_whenCacheEmpty() {
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("No cache")
            .withUrl("https://example.com/uncached-" + UUID.randomUUID())
        );

        RestAssured.given()
            .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
            .pathParam("bookmarkId", bookmark.getId().getUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot")
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn204_whenOnlyNegativeCacheEntryExists() {
        var url = "https://example.com/negative-" + UUID.randomUUID();
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("Negative")
            .withUrl(url)
        );
        cacheService.putNegative(ScreenshotCacheService.keyFor(bookmark.getUrl()));

        RestAssured.given()
            .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
            .pathParam("bookmarkId", bookmark.getId().getUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot")
            .then()
            .statusCode(204);

        cacheService.deleteForKey(ScreenshotCacheService.keyFor(bookmark.getUrl()));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn204_whenBookmarkDoesNotExist() {
        // The read path resolves the bookmark URL via a scalar projection, not a
        // full entity load (which would extract OffsetDateTime columns through
        // Hibernate's non-thread-safe shared UTC Calendar, HHH-20355). A missing
        // bookmark in an accessible collection therefore yields "no screenshot"
        // (204) instead of throwing entity-not-found.
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("Access anchor")
            .withUrl("https://example.com/anchor-" + UUID.randomUUID())
        );

        RestAssured.given()
            .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
            .pathParam("bookmarkId", UUID.randomUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot")
            .then()
            .statusCode(204);
    }

    @Test
    void shouldReturn401_whenRefreshingUnauthenticated() {
        RestAssured.given()
            .pathParam("collectionId", UUID.randomUUID().toString())
            .pathParam("bookmarkId", UUID.randomUUID().toString())
            .post("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot/refresh")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenRefreshingCollectionAccessDenied() {
        RestAssured.given()
            .pathParam("collectionId", UUID.randomUUID().toString())
            .pathParam("bookmarkId", UUID.randomUUID().toString())
            .post("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot/refresh")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReplaceExistingCacheEntry_whenRefreshing() {
        // The pre-refresh payload is a sentinel we can recognise; whatever
        // captureNow produces afterwards — fresh image bytes if the sidecar
        // is reachable, or a negative entry if not — the original sentinel
        // must be gone. That swap is the signal that refresh actually dropped
        // the old entry and re-captured.
        var url = "https://example.com/refresh-" + UUID.randomUUID();
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("Refresh target")
            .withUrl(url)
        );
        byte[] sentinel = new byte[]{1, 2, 3, 4};
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());
        cacheService.putSuccess(key, sentinel, "image/jpeg");

        try {
            RestAssured.given()
                .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
                .pathParam("bookmarkId", bookmark.getId().getUUID().toString())
                .post("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot/refresh")
                .then()
                .statusCode(204);

            var after = cacheService.get(key);
            // Either the sidecar was reachable (negative=false, fresh bytes)
            // or it wasn't (negative=true, empty bytes). Either way the cached
            // bytes must no longer be our sentinel.
            org.assertj.core.api.Assertions.assertThat(after).isPresent();
            org.assertj.core.api.Assertions.assertThat(after.get().bytes()).isNotEqualTo(sentinel);
        } finally {
            cacheService.deleteForKey(key);
        }
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn200WithBytes_whenCacheHit() {
        var url = "https://example.com/cached-" + UUID.randomUUID();
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("Cached")
            .withUrl(url)
        );
        byte[] payload = new byte[]{ (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0, 0, 0 };
        String key = ScreenshotCacheService.keyFor(bookmark.getUrl());
        cacheService.putSuccess(key, payload, "image/jpeg");

        try {
            byte[] body = RestAssured.given()
                .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
                .pathParam("bookmarkId", bookmark.getId().getUUID().toString())
                .get("/collections/{collectionId}/bookmarks/{bookmarkId}/screenshot")
                .then()
                .statusCode(200)
                .header("Content-Type", "image/jpeg")
                .extract().asByteArray();

            org.assertj.core.api.Assertions.assertThat(body).isEqualTo(payload);
        } finally {
            cacheService.deleteForKey(key);
        }
    }
}
