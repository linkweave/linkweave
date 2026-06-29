package org.linkweave.api.screenshot;

import org.linkweave.api.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ScreenshotResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    UserRepo userRepo;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenGetScreenshotWithMismatchedCollectionId() {
        // ARRANGE
        // collectionA: the test user has access
        Collection collectionA = fixtureService.createTestCollection(b -> b.withName("Collection A"));

        // collectionB: owned by alice (seeded user), NO CollectionAccess for test user
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection collectionB = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection")
        );
        Bookmark bookmarkInB = fixtureService.persistBookmark(b -> b
            .withCollection(collectionB)
            .withTitle("Alice's Bookmark")
            .withUrl("https://alice.example.com")
        );

        // IDOR attack: supply collectionA (has access) but bookmark belongs to collectionB (no access)
        // ACT
        RestAssured.given()
            .get("/collections/{cid}/bookmarks/{bid}/screenshot",
                collectionA.getId().getUUID(), bookmarkInB.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenGetScreenshotWithWrongCollectionDespiteHavingAccessToBoth() {
        // ARRANGE
        // User has access to BOTH collections, but the path says collectionA while bookmark is in collectionB.
        // requireSameCollection catches this mismatch even though requireAccessTo would pass.
        Collection collectionA = fixtureService.createTestCollection(b -> b.withName("Collection A"));
        Collection collectionB = fixtureService.createTestCollection(b -> b.withName("Collection B"));
        Bookmark bookmarkInB = fixtureService.persistBookmark(b -> b
            .withCollection(collectionB)
            .withTitle("Collection B Bookmark")
            .withUrl("https://b.example.com")
        );

        // ACT
        RestAssured.given()
            .get("/collections/{cid}/bookmarks/{bid}/screenshot",
                collectionA.getId().getUUID(), bookmarkInB.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnNoContent_whenUserHasAccessButNoScreenshotCached() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("Test Bookmark")
            .withUrl("https://example.com")
        );

        // ACT
        RestAssured.given()
            .get("/collections/{cid}/bookmarks/{bid}/screenshot",
                collection.getId().getUUID(), bookmark.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenRefreshScreenshotWithMismatchedCollectionId() {
        // ARRANGE
        Collection collectionA = fixtureService.createTestCollection(b -> b.withName("Collection A"));

        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection collectionB = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection")
        );
        Bookmark bookmarkInB = fixtureService.persistBookmark(b -> b
            .withCollection(collectionB)
            .withTitle("Alice's Bookmark")
            .withUrl("https://alice.example.com")
        );

        // ACT
        RestAssured.given()
            .post("/collections/{cid}/bookmarks/{bid}/screenshot/refresh",
                collectionA.getId().getUUID(), bookmarkInB.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenGetScreenshotForCollectionWithoutAccess() {
        // ARRANGE
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection otherCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection")
        );
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(otherCollection)
            .withTitle("Alice's Bookmark")
            .withUrl("https://alice.example.com")
        );

        // ACT
        RestAssured.given()
            .get("/collections/{cid}/bookmarks/{bid}/screenshot",
                java.util.UUID.randomUUID(), bookmark.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);
    }
}
