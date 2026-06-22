package org.linkweave.api.collection;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AutoProvisionITest {

    @Inject
    CollectionService collectionService;

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    DatabaseService databaseService;

    @Inject
    FixtureService fixtureService;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void shouldAutoProvisionDefaultCollectionForNewUser() {
        User newuser = fixtureService.persistUser(userBuilder -> {
            UUID uuid = UUID.randomUUID();
            userBuilder
                .withEmail(uuid + "@example.com")
                .withVorname(uuid + "_vorname")
                .withNachname(uuid + "_nachname");
        });

        collectionService.getDefaultCollectionOrAutoprovision(newuser);

        var collections = collectionRepo.findByOwner(newuser.getId());
        Assertions.assertThat(collections).hasSize(1);
        var created = collections.getFirst();
        Assertions.assertThat(created.getName()).isEqualTo("My Bookmarks");
        Assertions.assertThat(created.getOwner().getId()).isEqualTo(newuser.getId());

        var accessList = collectionAccessRepo.findByUser(newuser.getId());
        Assertions.assertThat(accessList).hasSize(1);
        var access = accessList.getFirst();
        Assertions.assertThat(access.getRole()).isEqualTo(CollectionRole.OWNER);
        Assertions.assertThat(access.isDefault()).isTrue();
        Assertions.assertThat(access.getCollection().getId()).isEqualTo(created.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = { "BOOKMARK_WRITE" }
    )
    void shouldNotCreateDuplicateCollectionsOnSecondProvision() {

        User testusr = fixtureService.persistUser(userBuilder -> {
            UUID uuid = UUID.randomUUID();
            userBuilder
                .withEmail(uuid + "@example.com")
                .withVorname(uuid + "_vorname")
                .withNachname(uuid + "_nachname");
        });

        collectionService.getDefaultCollectionOrAutoprovision(testusr);
        collectionService.getDefaultCollectionOrAutoprovision(testusr);

        var collections = collectionRepo.findByOwner(testusr.getId());
        Assertions.assertThat(collections).hasSize(1);
        var created = collections.getFirst();
        Assertions.assertThat(created.getName()).isEqualTo("My Bookmarks");

        var accessList = collectionAccessRepo.findByUser(testusr.getId());
        Assertions.assertThat(accessList).hasSize(1);
        Assertions.assertThat(accessList.getFirst().getCollection().getId()).isEqualTo(created.getId());
    }
}
