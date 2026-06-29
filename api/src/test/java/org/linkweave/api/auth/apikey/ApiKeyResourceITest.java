package org.linkweave.api.auth.apikey;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.linkweave.infrastructure.clock.AppClock;
import org.linkweave.infrastructure.clock.ClockProvider;
import org.linkweave.api.types.id.ID;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.linkweave.api.auth.EnsureUserService;
import org.linkweave.api.auth.apikey.json.ApiKeyCreateJson;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class ApiKeyResourceITest {

    @Inject
    FixtureService fixtureService;

    @Inject
    ApiKeyRepo apiKeyRepo;

    @Inject
    ClockProvider clockProvider;

    @Inject
    ApiKeyService apiKeyService;

    @Inject
    EnsureUserService ensureUserService;

    @Inject
    AppClock appClock;

    @AfterEach
    void resetClock() {
        clockProvider.reset();
    }

    /**
     * The test database file survives across runs and the {@code @TestSecurity} users are fixed,
     * so leftover keys from a previous run would trip the max-active-keys check. Start each test
     * from an empty ApiKey table instead.
     */
    @BeforeEach
    void deleteAllApiKeys() {
        fixtureService.deleteAllApiKeys();
    }

    /** 64 hex chars, unique per call — satisfies the keyHash format and unique constraint. */
    private static String uniqueKeyHash() {
        return (UUID.randomUUID() + "" + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    @Test
    void shouldReturn401_whenListNotAuthenticated() {
        RestAssured.given()
            .get("/auth/api-keys")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldCreateApiKey() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Create Test Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .body("name", equalTo("Create Test Key"))
            .body("prefix", notNullValue())
            .body("prefix.length()", greaterThanOrEqualTo(8))
            .body("createdAt", notNullValue())
            .body("expiresAt", nullValue())
            .body("key", startsWith("lw_"))
            .body("key.length()", equalTo(67));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldCreateApiKeyWithExpiration() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Expiring Key\",\"expiresIn\":\"90d\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .body("name", equalTo("Expiring Key"))
            .body("expiresAt", notNullValue())
            .body("key", startsWith("lw_"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldListCreatedKeys() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"List Test Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201);

        RestAssured.given()
            .get("/auth/api-keys")
            .then()
            .statusCode(200)
            .body("apiKeys.name", hasItem(equalTo("List Test Key")));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRevokeApiKey() {
        String id = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Key to Revoke\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("id");

        RestAssured.given()
            .delete("/auth/api-keys/" + id)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenRevokingNonexistentKey() {
        RestAssured.given()
            .delete("/auth/api-keys/" + UUID.randomUUID())
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRejectBlankName() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"   \"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRejectNameTooLong() {
        // ARRANGE
        String longName = "x".repeat(101);
        // ACT
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"" + longName + "\"}")
            .post("/auth/api-keys")
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldAuthenticateWithApiKey() {
        String fullKey = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Auth Test Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("key");

        RestAssured.given()
            .header("X-API-Key", fullKey)
            .get("/auth/api-keys")
            .then()
            .statusCode(200);
    }

    @Test
    void shouldReturn401_whenInvalidApiKey() {
        RestAssured.given()
            .header("X-API-Key", "lw_" + "a".repeat(64))
            .get("/auth/api-keys")
            .then()
            .statusCode(401);
    }

    @Test
    void shouldReturn401_whenMalformedApiKey() {
        RestAssured.given()
            .header("X-API-Key", "not-a-valid-key")
            .get("/auth/api-keys")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenRevokingKeyOfAnotherUser() {
        // ARRANGE
        User otherUser = fixtureService.persistUser(u -> u.withEmail("other-apikey-" + UUID.randomUUID() + "@example.com"));
        String hash = uniqueKeyHash();
        var otherKey = fixtureService.persistApiKey(k -> k
            .withUser(otherUser)
            .withName("Other user key")
            .withKeyHash(hash)
            .withKeyPrefix(hash.substring(0, 8)));

        // ACT
        RestAssured.given()
            .delete("/auth/api-keys/" + otherKey.getId().getUUID())
            // ASSERT
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldFailServiceLevelRevoke_whenKeyBelongsToAnotherUser() {
        ensureUserService.ensureUserExists();
        User otherUser = fixtureService.persistUser(u -> u.withEmail("revoke-other-" + UUID.randomUUID() + "@example.com"));
        String hash = uniqueKeyHash();
        ApiKey otherKey = fixtureService.persistApiKey(k -> k
            .withUser(otherUser)
            .withName("Foreign key")
            .withKeyHash(hash)
            .withKeyPrefix(hash.substring(0, 8)));

        // Driven at the service level, bypassing the resource's requireApiKeyOwnership guard:
        // the user-scoped lookup inside revokeApiKey must refuse a key owned by another user.
        assertThatThrownBy(() -> apiKeyService.revokeApiKey(otherKey.getId()))
            .isInstanceOf(AppValidationException.class);
        assertThat(apiKeyRepo.findByIdAndUser(otherKey.getId(), otherUser.getId()))
            .hasValueSatisfying(key -> assertThat(key.getRevokedAt()).isNull());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn400_whenRevokingAlreadyRevokedKey() {
        String id = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Key to revoke twice\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("id");

        RestAssured.given()
            .delete("/auth/api-keys/" + id)
            .then()
            .statusCode(204);

        RestAssured.given()
            .delete("/auth/api-keys/" + id)
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldIncludeRevokedAtInListAfterRevocation() {
        String id = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Key to check revokedAt\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("id");

        RestAssured.given()
            .get("/auth/api-keys")
            .then()
            .statusCode(200)
            .body("apiKeys.find { it.id == '" + id + "'}.revokedAt", nullValue());

        RestAssured.given()
            .delete("/auth/api-keys/" + id)
            .then()
            .statusCode(204);

        RestAssured.given()
            .get("/auth/api-keys")
            .then()
            .statusCode(200)
            .body("apiKeys.find { it.id == '" + id + "'}.revokedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldDebounceLastUsedAtWithinWindow() {
        String uniqueHash = uniqueKeyHash();
        User user = fixtureService.persistUser(u -> u.withEmail("debounce-apikey-" + UUID.randomUUID() + "@example.com"));
        ApiKey key = fixtureService.persistApiKey(k -> k
            .withUser(user)
            .withName("Debounce key")
            .withKeyHash(uniqueHash)
            .withKeyPrefix(uniqueHash.substring(0, 8)));
        ID<ApiKey> id = key.getId();

        Instant t0 = Instant.parse("2026-06-09T12:00:00Z");
        clockProvider.resetUsing(t0);
        // First use: last_used_at was null, so the write happens.
        assertThat(apiKeyRepo.updateLastUsedAt(id)).isEqualTo(1L);

        // Immediate re-use within the 5-minute window: debounced, no write.
        assertThat(apiKeyRepo.updateLastUsedAt(id)).isZero();

        // Two minutes later, still inside the window: still debounced.
        clockProvider.resetUsing(t0.plusSeconds(120));
        assertThat(apiKeyRepo.updateLastUsedAt(id)).isZero();

        // Past the window: the write happens again.
        clockProvider.resetUsing(t0.plusSeconds(6 * 60));
        assertThat(apiKeyRepo.updateLastUsedAt(id)).isEqualTo(1L);
    }

    @Test
    @TestSecurity(user = "maxkeys@example.com", roles = {"BOOKMARK_READ"})
    void shouldRejectCreate_whenMaxActiveKeysReached() {
        // Dedicated, freshly provisioned user so the active count starts at zero (BR-001: max 10).
        // Driven at the service level: the resource's @RateLimit would otherwise cap creates.
        ensureUserService.ensureUserExists();
        for (int i = 0; i < 10; i++) {
            apiKeyService.createApiKey(new ApiKeyCreateJson("Key " + i, null));
        }

        assertThatThrownBy(() -> apiKeyService.createApiKey(new ApiKeyCreateJson("Eleventh", null)))
            .isInstanceOf(AppValidationException.class);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotBuildIdentity_forExpiredKey() {
        User user = fixtureService.persistUser(u -> u.withEmail("expired-apikey-" + UUID.randomUUID() + "@example.com"));

        String expiredHash = uniqueKeyHash();
        fixtureService.persistApiKey(k -> k
            .withUser(user)
            .withName("Expired key")
            .withKeyHash(expiredHash)
            .withKeyPrefix(expiredHash.substring(0, 8))
            .withExpiresAt(appClock.offsetDateTime().now().minusDays(1)));

        String activeHash = uniqueKeyHash();
        fixtureService.persistApiKey(k -> k
            .withUser(user)
            .withName("Active key")
            .withKeyHash(activeHash)
            .withKeyPrefix(activeHash.substring(0, 8)));

        // findActiveByHash filters expired keys, so authentication builds no identity for one.
        assertThat(apiKeyService.buildIdentityFromApiKey(expiredHash)).isEmpty();
        // Control: a non-expired key with the same owner is still accepted.
        assertThat(apiKeyService.buildIdentityFromApiKey(activeHash)).isPresent();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotBuildIdentity_forKeyOfDeactivatedUser() {
        User deactivatedUser = fixtureService.persistUser(u -> u
            .withEmail("deactivated-apikey-" + UUID.randomUUID() + "@example.com")
            .withAktiv(false));

        String hash = uniqueKeyHash();
        fixtureService.persistApiKey(k -> k
            .withUser(deactivatedUser)
            .withName("Key of deactivated user")
            .withKeyHash(hash)
            .withKeyPrefix(hash.substring(0, 8)));

        // API key auth bypasses the IdP, so the aktiv flag must gate it instead.
        assertThat(apiKeyService.buildIdentityFromApiKey(hash)).isEmpty();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldAuthenticateWithApiKeyAndAccessCollections() {
        String fullKey = RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"name\":\"Collection Access Key\"}")
            .post("/auth/api-keys")
            .then()
            .statusCode(201)
            .extract().path("key");

        fixtureService.createTestCollection();

        RestAssured.given()
            .header("X-API-Key", fullKey)
            .get("/collections")
            .then()
            .statusCode(200);
    }
}
