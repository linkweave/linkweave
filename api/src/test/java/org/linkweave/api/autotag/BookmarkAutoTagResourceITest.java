package org.linkweave.api.autotag;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.autotag.llm.FakeLlmTaggingClient;
import org.linkweave.api.autotag.llm.LlmTaggingClient;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.emailaddress.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BookmarkAutoTagResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    UserRepo userRepo;

    private final FakeLlmTaggingClient fake = new FakeLlmTaggingClient();

    @BeforeEach
    void installFake() {
        fake.reset();
        QuarkusMock.installMockForType(fake, LlmTaggingClient.class);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnConstrainedSuggestionsForBookmark() {
        Collection collection = fixtureService.createTestCollection();
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));
        fixtureService.persistTag(b -> b.withCollection(collection).withName("databases"));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("Async Rust")
            .withUrl("https://example.com/rust"));
        fake.namesToReturn = List.of("rust", "not-a-real-tag");

        given()
            .post("/collections/{cid}/autotag/bookmarks/{bid}/suggest-tags",
                collection.getId().getUUID(), bookmark.getId().getUUID())
            .then()
            .statusCode(200)
            .body("tagList.size()", is(1))
            .body("tagList.data.name", hasItem("rust"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnActiveProviderOnWarmUp() {
        Collection collection = fixtureService.createTestCollection();

        given()
            .post("/collections/{cid}/autotag/warm-up", collection.getId().getUUID())
            .then()
            .statusCode(200)
            .body("provider", is("ollama"))
            .body("model", is("gemma2:2b"))
            .body("onDevice", is(true));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403ForBookmarkInCollectionWithoutAccess() {
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection otherCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(otherCollection)
            .withTitle("Alice's Bookmark")
            .withUrl("https://alice.example.com"));

        given()
            .post("/collections/{cid}/autotag/bookmarks/{bid}/suggest-tags",
                UUID.randomUUID(), bookmark.getId().getUUID())
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnConstrainedSuggestionsForText() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection();
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));
        fixtureService.persistTag(b -> b.withCollection(collection).withName("databases"));
        fake.namesToReturn = List.of("rust", "not-a-real-tag");

        String body = """
            {"title":"Async Rust","url":"https://example.com/rust"}
            """;

        // ACT
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/collections/{cid}/autotag/suggest-tags", collection.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(200)
            .body("tagList.size()", is(1))
            .body("tagList.data.name", hasItem("rust"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403ForTextInCollectionWithoutAccess() {
        // ARRANGE
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection otherCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));

        String body = """
            {"title":"Alice's Bookmark","url":"https://alice.example.com"}
            """;

        // ACT
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/collections/{cid}/autotag/suggest-tags", otherCollection.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);
    }
}
