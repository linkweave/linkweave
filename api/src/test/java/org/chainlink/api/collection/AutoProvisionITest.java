package org.chainlink.api.collection;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
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
        Assertions.assertThat(collections.get(0).name).isEqualTo("My Bookmarks");
        Assertions.assertThat(collections.get(0).owner.getId()).isEqualTo(user.getId());

        var accessList = collectionAccessRepo.findByUser(user.getId());
        Assertions.assertThat(accessList).hasSize(1);
        Assertions.assertThat(accessList.get(0).role).isEqualTo(CollectionRole.OWNER);
        Assertions.assertThat(accessList.get(0).isDefault).isTrue();
        Assertions.assertThat(accessList.get(0).collection.getId()).isEqualTo(collections.get(0).getId());
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

        var accessList = collectionAccessRepo.findByUser(user.getId());
        Assertions.assertThat(accessList).hasSize(1);
    }
}
