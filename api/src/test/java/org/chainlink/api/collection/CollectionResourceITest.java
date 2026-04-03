package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.shared.user.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class CollectionResourceITest {

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    UserRepo userRepo;

    private Collection createTestCollection() {
        User user = userRepo.findByEmail(EmailAddress.fromString("test@example.com")).orElseThrow();
        Collection collection = new Collection();
        collection.setName("Test Collection");
        collection.setOwner(user);
        collectionRepo.persist(collection);

        CollectionAccess access = new CollectionAccess();
        access.setCollection(collection);
        access.setUser(user);
        access.setRole(CollectionRole.OWNER);
        access.setDefault(true);
        collectionAccessRepo.persist(access);

        return collection;
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .get("/collections/{id}")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturn403_whenNoCollectionAccess() {
        RestAssured.given()
            .pathParam("id", UUID.randomUUID().toString())
            .get("/collections/{id}")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_READ"}
    )
    void shouldReturnCollectionInfo_whenUserHasAccess() {
        Collection collection = createTestCollection();

        RestAssured.given()
            .pathParam("id", collection.getId().getUUID().toString())
            .get("/collections/{id}")
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("name", equalTo("Test Collection"))
            .body("bookmarks", hasSize(0))
            .body("folders", hasSize(0))
            .body("tags", hasSize(0));
    }
}
