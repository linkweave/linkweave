package org.chainlink.api.collection.favicon;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.user.User;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FaviconResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    UserRepo userRepo;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenGetFaviconWithMismatchedCollectionId() {
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

        // IDOR: supply collectionA (has access) but bookmark belongs to collectionB (no access)
        RestAssured.given()
            .get("/collections/{cid}/bookmarks/{bid}/favicon",
                collectionA.getId().getUUID(), bookmarkInB.getId().getUUID())
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnNoContent_whenUserHasAccessButNoFaviconCached() {
        Collection collection = fixtureService.createTestCollection();
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("Test Bookmark")
            .withUrl("https://example.com")
        );

        RestAssured.given()
            .get("/collections/{cid}/bookmarks/{bid}/favicon",
                collection.getId().getUUID(), bookmark.getId().getUUID())
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenGetFaviconForCollectionWithoutAccess() {
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

        RestAssured.given()
            .get("/collections/{cid}/bookmarks/{bid}/favicon",
                java.util.UUID.randomUUID(), bookmark.getId().getUUID())
            .then()
            .statusCode(403);
    }
}
