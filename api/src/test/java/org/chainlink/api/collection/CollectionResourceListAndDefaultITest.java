package org.chainlink.api.collection;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class CollectionResourceListAndDefaultITest {

    @Inject
    FixtureService fixtureService;

    @Test
    void shouldReturn401_whenListCollectionsNotAuthenticated() {
        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldListCollectionsIncludingNewlyCreatedOnes() {
        var col1 = fixtureService.createTestCollection();
        var owner = col1.getOwner();

        var col2 = fixtureService.persistCollection(b -> b.withName("Work-List").withOwner(owner));
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(col2)
            .withUser(owner)
            .withRole(CollectionRole.OWNER)
            .withDefault(false)
        );

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("name", hasItems("Test Collection", "Work-List"))
            .body("findAll { it.name == 'Work-List' }.size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldIncludeIsDefaultAndRoleFieldsInList() {
        var col = fixtureService.createTestCollection();

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("findAll { it.id == '" + col.getId().getUUID() + "' }.size()", equalTo(1))
            .body("find { it.id == '" + col.getId().getUUID() + "' }.role", equalTo("OWNER"))
            .body("find { it.id == '" + col.getId().getUUID() + "' }.isDefault", equalTo(false));
    }

    @Test
    void shouldReturn401_whenSetDefaultNotAuthenticated() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .put("/collections/{id}/default")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturn403_whenSetDefaultWithoutAccess() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .put("/collections/{id}/default")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldSetDefaultCollection() {
        var col1 = fixtureService.createTestCollection();
        var owner = col1.getOwner();

        var col2 = fixtureService.persistCollection(b -> b.withName("Work-Default").withOwner(owner));
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(col2)
            .withUser(owner)
            .withRole(CollectionRole.OWNER)
            .withDefault(false)
        );

        RestAssured.given()
            .pathParam("id", col2.getId().getUUID().toString())
            .put("/collections/{id}/default")
            .then()
            .statusCode(204);

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("find { it.id == '" + col2.getId().getUUID() + "' }.isDefault", equalTo(true))
            .body("find { it.id == '" + col1.getId().getUUID() + "' }.isDefault", equalTo(false));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldHaveExactlyOneDefaultAfterSwitch() {
        var col1 = fixtureService.createTestCollection();
        var owner = col1.getOwner();

        var col2 = fixtureService.persistCollection(b -> b.withName("B").withOwner(owner));
        var col3 = fixtureService.persistCollection(b -> b.withName("C").withOwner(owner));

        fixtureService.persistCollectionAccess(b -> b
            .withCollection(col2).withUser(owner).withRole(CollectionRole.OWNER).withDefault(false)
        );
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(col3).withUser(owner).withRole(CollectionRole.OWNER).withDefault(false)
        );

        RestAssured.given()
            .pathParam("id", col3.getId().getUUID().toString())
            .put("/collections/{id}/default")
            .then()
            .statusCode(204);

        RestAssured.given()
            .get("/collections")
            .then()
            .statusCode(200)
            .body("find { it.id == '" + col3.getId().getUUID() + "' }.isDefault", equalTo(true))
            .body("find { it.id == '" + col1.getId().getUUID() + "' }.isDefault", equalTo(false))
            .body("find { it.id == '" + col2.getId().getUUID() + "' }.isDefault", equalTo(false));
    }
}
