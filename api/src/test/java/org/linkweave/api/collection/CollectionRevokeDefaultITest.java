package org.linkweave.api.collection;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;

@QuarkusTest
class CollectionRevokeDefaultITest {

    @Inject
    CollectionService collectionService;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReassignDefaultWhenRevokedCollectionWasDefault() {
        // ARRANGE
        var owner = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@ex.com")
            .withVorname("Owner")
            .withNachname("User")
        );
        var member = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@ex.com")
            .withVorname("Member")
            .withNachname("User")
        );

        // Both users get their own default collection via autoprovision
        collectionService.getDefaultCollectionOrAutoprovision(owner);
        collectionService.getDefaultCollectionOrAutoprovision(member);

        // Owner creates a second collection and shares it with member
        var sharedCollection = collectionService.createCollection("Shared", owner);
        collectionService.shareWithUser(sharedCollection.getId(), member.getEmail(), CollectionRole.MEMBER, owner);

        // Member sets the shared collection as their default
        collectionService.setDefaultCollection(sharedCollection.getId(), member);

        var accessesBeforeRevoke = collectionAccessRepo.findByUser(member.getId());
        long defaultsBefore = accessesBeforeRevoke.stream().filter(CollectionAccess::isDefault).count();
        Assertions.assertThat(defaultsBefore).isEqualTo(1);
        Assertions.assertThat(accessesBeforeRevoke.stream()
            .filter(CollectionAccess::isDefault)
            .findFirst().orElseThrow()
            .getCollection().getId()
        ).isEqualTo(sharedCollection.getId());

        // ACT
        // Owner revokes member's access
        collectionService.revokeAccess(sharedCollection.getId(), member.getId());

        // ASSERT
        // Member still has exactly one default — their own autoprovisionned collection
        var accessesAfterRevoke = collectionAccessRepo.findByUser(member.getId());
        Assertions.assertThat(accessesAfterRevoke).hasSize(1);
        Assertions.assertThat(accessesAfterRevoke.getFirst().isDefault()).isTrue();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldKeepDefaultUnchangedWhenRevokingNonDefaultAccess() {
        // ARRANGE
        var owner = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@ex.com")
            .withVorname("Owner")
            .withNachname("User")
        );
        var member = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@ex.com")
            .withVorname("Member")
            .withNachname("User")
        );

        collectionService.getDefaultCollectionOrAutoprovision(owner);
        var memberOwnCollection = collectionService.getDefaultCollectionOrAutoprovision(member);

        // Owner shares a collection with member, but member does NOT set it as default
        var sharedCollection = collectionService.createCollection("Shared", owner);
        collectionService.shareWithUser(sharedCollection.getId(), member.getEmail(), CollectionRole.MEMBER, owner);

        // ACT
        // Owner revokes member's access to the shared (non-default) collection
        collectionService.revokeAccess(sharedCollection.getId(), member.getId());

        // ASSERT
        // Member's own default collection is still the default
        var remaining = collectionAccessRepo.findByUser(member.getId());
        Assertions.assertThat(remaining).hasSize(1);
        Assertions.assertThat(remaining.getFirst().isDefault()).isTrue();
        Assertions.assertThat(remaining.getFirst().getCollection().getId())
            .isEqualTo(memberOwnCollection.getId());
    }
}
