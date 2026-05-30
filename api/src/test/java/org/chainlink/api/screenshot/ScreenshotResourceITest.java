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
