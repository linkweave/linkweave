package org.linkweave.api.collection.events;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-104 from a non-browser client — the CLI, which authenticates with
 * {@code X-API-Key} rather than a session cookie.
 *
 * <p>Two things about that path are easy to break and invisible until someone
 * has a terminal and a browser open at once:
 *
 * <ul>
 *   <li><strong>Attribution depends on the API-key identity carrying an email.</strong>
 *       {@code ApiKeyIdentityProvider} sets the principal to the user's address, which is
 *       what lets {@code CurrentUserService} resolve them. Change it to the key's id or
 *       name and {@code currentUserName()} would fail to parse it as an email — and since
 *       every write now resolves the actor for BR-209, that would not merely drop the
 *       attribution, it would throw inside <em>every CLI write</em>. Nothing else in the
 *       suite covers a write over this transport.</li>
 *   <li><strong>The CLI deliberately sends no {@code X-Client-Id}.</strong> It is not a
 *       browser tab, so there is nothing to filter out; a terminal command must reach the
 *       user's own open tabs, which is exactly what a null origin does (BR-205).</li>
 * </ul>
 */
@QuarkusTest
class ApiKeyClientNotificationITest {

    @Inject
    CollectionEventBroadcaster broadcaster;

    @Inject
    FixtureService fixtureService;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ", "BOOKMARK_WRITE"})
    void shouldAnnounceAndAttributeAWriteMadeWithAnApiKey() {
        // ARRANGE — a key, as `linkweave login` would obtain one
        Collection collection = fixtureService.createTestCollection();
        String apiKey = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"CLI notification test\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("key");

        AssertSubscriber<CollectionEventJson> subscriber = broadcaster.subscribe(collection.getId(), "a-browser-tab")
            .subscribe().withSubscriber(AssertSubscriber.create(10));

        // ACT — the write arrives the way `linkweave bookmarks add` sends it:
        // API key, no client id, no session cookie
        String body = """
            {"collectionId":"%s","title":"From the CLI","url":"https://example.com/cli"}
            """.formatted(collection.getId().getUUID());
        RestAssured.given()
            .header("X-API-Key", apiKey)
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(200);

        // ASSERT
        assertThat(subscriber.getItems()).hasSize(1);
        CollectionEventJson event = subscriber.getItems().getFirst();
        assertThat(event.getKind()).isEqualTo(ChangeKind.BOOKMARK_ADDED);
        assertThat(event.getActorName())
            .as("an API key identifies a person, so their change is attributed like any other")
            .isNotBlank();
        assertThat(event.getOriginClientId())
            .as("the CLI is no browser tab, so its change reaches every tab including the user's own")
            .isNull();
        subscriber.cancel();
    }
}
