package org.chainlink.api.collection.favicon;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FaviconResourceITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        RestAssured.given()
            .pathParam("collectionId", UUID.randomUUID().toString())
            .pathParam("bookmarkId", UUID.randomUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/favicon")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenCollectionAccessDenied() {
        RestAssured.given()
            .pathParam("collectionId", UUID.randomUUID().toString())
            .pathParam("bookmarkId", UUID.randomUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/favicon")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn204_whenBookmarkUrlIsLoopback_ssrfBlocked() {
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("Internal")
            .withUrl("http://127.0.0.1:9999/page")
        );

        RestAssured.given()
            .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
            .pathParam("bookmarkId", bookmark.getId().getUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/favicon")
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn204_whenBookmarkUrlIsPrivateRange_ssrfBlocked() {
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("Private")
            .withUrl("http://10.0.0.5/")
        );

        RestAssured.given()
            .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
            .pathParam("bookmarkId", bookmark.getId().getUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/favicon")
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn204_whenBookmarkUrlIsCloudMetadata_ssrfBlocked() {
        var bookmark = fixtureService.createTestBookmark(b -> b
            .withTitle("Metadata")
            .withUrl("http://169.254.169.254/latest/meta-data/")
        );

        RestAssured.given()
            .pathParam("collectionId", bookmark.getCollection().getId().getUUID().toString())
            .pathParam("bookmarkId", bookmark.getId().getUUID().toString())
            .get("/collections/{collectionId}/bookmarks/{bookmarkId}/favicon")
            .then()
            .statusCode(204);
    }
}
