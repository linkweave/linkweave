package org.linkweave.api.autotag;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.util.List;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkweave.api.autotag.llm.FakeLlmTaggingClient;
import org.linkweave.api.autotag.llm.LlmTaggingClient;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.emailaddress.EmailAddress;

/**
 * Per-collection opt-out for AI tag suggestions (UC-112 / FR-105).
 *
 * <p>The rule under test is BR-112-3: "off" means no model work at all for that
 * collection, not merely a hidden section in the dialog. Hiding it client-side
 * would make the setting a display preference; the whole point is that the
 * collection's bookmark text is never sent anywhere, which only holds if the
 * request is not made. Every assertion here is therefore about
 * {@code fake.suggestCalled} / {@code fake.warmUpCalled} being false, not about
 * what came back.
 */
@QuarkusTest
class BookmarkAutoTagCollectionOptOutITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    UserRepo userRepo;

    private final FakeLlmTaggingClient fake = new FakeLlmTaggingClient();

    @BeforeEach
    void installFake() {
        fake.reset();
        fake.namesToReturn = List.of("rust");
        QuarkusMock.installMockForType(fake, LlmTaggingClient.class);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotCallTheModelForAnOptedOutCollection() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection(b -> b.withAiTaggingEnabled(false));
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));

        // ACT
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"title":"Async Rust","url":"https://example.com/rust"}
                """)
            .post("/collections/{cid}/autotag/suggest-tags", collection.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(200)
            .body("tagList.size()", is(0))
            .body("status", is("DISABLED"));

        Assertions.assertThat(fake.suggestCalled)
            .as("an opted-out collection's bookmark text must never reach the model")
            .isFalse();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotCallTheModelForABookmarkInAnOptedOutCollection() {
        // ARRANGE
        Collection collection = fixtureService.createTestCollection(b -> b.withAiTaggingEnabled(false));
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));
        Bookmark bookmark = fixtureService.persistBookmark(b -> b
            .withCollection(collection)
            .withTitle("Async Rust")
            .withUrl("https://example.com/rust"));

        // ACT
        given()
            .post("/collections/{cid}/autotag/bookmarks/{bid}/suggest-tags",
                collection.getId().getUUID(), bookmark.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(200)
            .body("status", is("DISABLED"));

        Assertions.assertThat(fake.suggestCalled)
            .as("the bookmark-scoped endpoint is gated too, not only the text one")
            .isFalse();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotWarmUpTheModelForAnOptedOutCollection() {
        // ARRANGE — the dialog calls warm-up on every open, so leaving it ungated
        // would keep loading the model onto the host for a collection that opted
        // out (BR-112-3), even though no bookmark text would leave.
        Collection collection = fixtureService.createTestCollection(b -> b.withAiTaggingEnabled(false));

        // ACT
        given()
            .post("/collections/{cid}/autotag/warm-up", collection.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(200)
            .body("enabled", is(false));

        Assertions.assertThat(fake.warmUpCalled)
            .as("no model preload for an opted-out collection")
            .isFalse();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldSuggestNormallyWhenTheCollectionHasNotOptedOut() {
        // ARRANGE — the default. Existing collections must keep working after the
        // migration without anyone opting back in (BR-112-2).
        Collection collection = fixtureService.createTestCollection();
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));

        // ACT
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"title":"Async Rust","url":"https://example.com/rust"}
                """)
            .post("/collections/{cid}/autotag/suggest-tags", collection.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(200)
            .body("tagList.size()", is(1))
            .body("status", is("OK"));

        Assertions.assertThat(fake.suggestCalled).isTrue();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldLetAnyMemberWithAccessToggleTheSetting() {
        // ARRANGE — BR-112-5: not owner-only, unlike the screenshot toggle.
        Collection collection = fixtureService.createTestCollection();

        // ACT
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"enabled":false}
                """)
            .put("/collections/{id}/ai-tagging", collection.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(204);

        given()
            .post("/collections/{cid}/autotag/warm-up", collection.getId().getUUID())
            .then()
            .statusCode(200)
            .body("enabled", is(false));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRejectTheToggleFromSomeoneWithoutAccessToTheCollection() {
        // ARRANGE — "any member may change it" (BR-112-5) means any member of
        // *that* collection. Someone else's collection is still off limits.
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();
        Collection otherCollection = fixtureService.persistCollection(b -> b
            .withOwner(alice)
            .withName("Alice's Collection"));

        // ACT
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"enabled":false}
                """)
            .put("/collections/{id}/ai-tagging", otherCollection.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(403);
    }
}
