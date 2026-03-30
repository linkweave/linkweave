package org.chainlink.api.collection;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.db.DatabaseService;
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

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldAutoProvisionDefaultCollectionForNewUser() {
        User user = currentUserService.currentUser();

        collectionService.autoProvisionForUser(user);

        var collections = collectionRepo.findByOwner(user.getId());
        Assertions.assertThat(collections).hasSize(1);
        var created = collections.getFirst();
        Assertions.assertThat(created.name).isEqualTo("My Bookmarks");
        Assertions.assertThat(created.owner.getId()).isEqualTo(user.getId());

        var accessList = collectionAccessRepo.findByUser(user.getId());
        Assertions.assertThat(accessList).hasSize(1);
        var access = accessList.getFirst();
        Assertions.assertThat(access.role).isEqualTo(CollectionRole.OWNER);
        Assertions.assertThat(access.isDefault).isTrue();
        Assertions.assertThat(access.collection.getId()).isEqualTo(created.getId());
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldNotCreateDuplicateCollectionsOnSecondProvision() {
        User user = currentUserService.currentUser();

        collectionService.autoProvisionForUser(user);
        collectionService.autoProvisionForUser(user);

        var collections = collectionRepo.findByOwner(user.getId());
        Assertions.assertThat(collections).hasSize(1);
        var created = collections.getFirst();
        Assertions.assertThat(created.name).isEqualTo("My Bookmarks");

        var accessList = collectionAccessRepo.findByUser(user.getId());
        Assertions.assertThat(accessList).hasSize(1);
        Assertions.assertThat(accessList.getFirst().collection.getId()).isEqualTo(created.getId());
    }
}
