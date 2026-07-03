package org.linkweave.api.collection;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.db.DatabaseService;
import org.linkweave.infrastructure.errorhandling.AppValidationException;

/**
 * Service-level tests for the collection role model. These cover the business
 * invariants the {@link CollectionService} enforces regardless of caller (owner
 * immutability, single owner, member existence). Caller-authorization rules
 * (who may promote/demote/remove whom) live in the resource layer and are
 * verified over HTTP in {@link CollectionRoleAuthzHttpITest}.
 */
@QuarkusTest
class CollectionAdminRoleITest {

    @Inject
    CollectionService collectionService;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    private User owner;
    private User admin;
    private User member;
    private ID<org.linkweave.api.collection.Collection> collectionId;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    // ARRANGE helper — builds an owner/admin/member triangle on one collection.
    private void setupSharedCollection() {
        owner = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@owner.com")
            .withVorname("Owner").withNachname("User"));
        admin = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@admin.com")
            .withVorname("Admin").withNachname("User"));
        member = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@member.com")
            .withVorname("Member").withNachname("User"));

        collectionId = collectionService.createCollection("Shared Col", owner).getId();
        collectionService.shareWithUser(collectionId, admin.getEmail(), CollectionRole.ADMIN, owner);
        collectionService.shareWithUser(collectionId, member.getEmail(), CollectionRole.MEMBER, owner);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldShareWithAdminRole() {
        // ARRANGE
        setupSharedCollection();
        var newAdmin = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@admin2.com").withVorname("A2").withNachname("User"));

        // ACT
        var result = collectionService.shareWithUser(collectionId, newAdmin.getEmail(), CollectionRole.ADMIN, owner);

        // ASSERT
        Assertions.assertThat(result.getRole()).isEqualTo(CollectionRole.ADMIN);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldShareWithMemberRole() {
        // ARRANGE
        setupSharedCollection();
        var newMember = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@new.com").withVorname("New").withNachname("User"));

        // ACT
        var result = collectionService.shareWithUser(collectionId, newMember.getEmail(), CollectionRole.MEMBER, owner);

        // ASSERT
        Assertions.assertThat(result.getRole()).isEqualTo(CollectionRole.MEMBER);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectInvitingUserAsOwner() {
        // ARRANGE
        setupSharedCollection();
        var newUser = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@new.com").withVorname("New").withNachname("User"));

        // ACT / ASSERT — a collection can only ever have one owner
        Assertions.assertThatThrownBy(() ->
                collectionService.shareWithUser(collectionId, newUser.getEmail(), CollectionRole.OWNER, owner))
            .isInstanceOfSatisfying(AppValidationException.class,
                ex -> Assertions.assertThat(ex.getClientKey()).isEqualTo("ShareCannotAssignOwner"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRevokeMember() {
        // ARRANGE
        setupSharedCollection();

        // ACT
        collectionService.revokeAccess(collectionId, member.getId());

        // ASSERT
        var remaining = collectionAccessRepo.findByCollection(collectionId);
        Assertions.assertThat(remaining).hasSize(2);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectRevokingOwner() {
        // ARRANGE
        setupSharedCollection();

        // ACT / ASSERT — the owner can never be removed
        Assertions.assertThatThrownBy(() ->
                collectionService.revokeAccess(collectionId, owner.getId()))
            .isInstanceOfSatisfying(AppValidationException.class,
                ex -> Assertions.assertThat(ex.getClientKey()).isEqualTo("CannotRevokeOwner"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectRevokingNonMember() {
        // ARRANGE
        setupSharedCollection();
        var stranger = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@stranger.com").withVorname("S").withNachname("User"));

        // ACT / ASSERT — revoking someone without access is a client error, not a 500
        Assertions.assertThatThrownBy(() ->
                collectionService.revokeAccess(collectionId, stranger.getId()))
            .isInstanceOfSatisfying(AppValidationException.class,
                ex -> Assertions.assertThat(ex.getClientKey()).isEqualTo("CollectionMemberNotFound"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldPromoteMemberToAdmin() {
        // ARRANGE
        setupSharedCollection();

        // ACT
        var result = collectionService.changeMemberRole(collectionId, member.getId(), CollectionRole.ADMIN);

        // ASSERT
        Assertions.assertThat(result.getRole()).isEqualTo(CollectionRole.ADMIN);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldDemoteAdminToMember() {
        // ARRANGE
        setupSharedCollection();

        // ACT
        var result = collectionService.changeMemberRole(collectionId, admin.getId(), CollectionRole.MEMBER);

        // ASSERT
        Assertions.assertThat(result.getRole()).isEqualTo(CollectionRole.MEMBER);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectAssigningOwnerRole() {
        // ARRANGE
        setupSharedCollection();

        // ACT / ASSERT
        Assertions.assertThatThrownBy(() ->
                collectionService.changeMemberRole(collectionId, member.getId(), CollectionRole.OWNER))
            .isInstanceOfSatisfying(AppValidationException.class,
                ex -> Assertions.assertThat(ex.getClientKey()).isEqualTo("CannotChangeOwnerRole"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectChangingOwnerRole() {
        // ARRANGE
        setupSharedCollection();

        // ACT / ASSERT
        Assertions.assertThatThrownBy(() ->
                collectionService.changeMemberRole(collectionId, owner.getId(), CollectionRole.MEMBER))
            .isInstanceOfSatisfying(AppValidationException.class,
                ex -> Assertions.assertThat(ex.getClientKey()).isEqualTo("CannotChangeOwnerRole"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldRejectChangingRoleForNonMember() {
        // ARRANGE
        setupSharedCollection();
        var stranger = fixtureService.persistUser(b -> b
            .withEmail(UUID.randomUUID() + "@stranger.com").withVorname("S").withNachname("User"));

        // ACT / ASSERT — changing a non-member's role is a client error, not a 500
        Assertions.assertThatThrownBy(() ->
                collectionService.changeMemberRole(collectionId, stranger.getId(), CollectionRole.ADMIN))
            .isInstanceOfSatisfying(AppValidationException.class,
                ex -> Assertions.assertThat(ex.getClientKey()).isEqualTo("CollectionMemberNotFound"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldPreserveNameWhenAdminUpdatesConfig() {
        // ARRANGE
        setupSharedCollection();
        String originalName = collectionService.getCollection(collectionId).getName();

        // ACT — admin updates allowlist + screenshot but tries to rename
        CollectionSummaryJson result = collectionService.updateCollection(
            collectionId, "Renamed By Admin", "example.com", true, admin, CollectionRole.ADMIN);

        // ASSERT — allowlist and screenshot changed, name did not
        Assertions.assertThat(result.getName()).isEqualTo(originalName);
        var refreshed = collectionService.getCollection(collectionId);
        Assertions.assertThat(refreshed.getName()).isEqualTo(originalName);
        Assertions.assertThat(refreshed.getBrowserFetchAllowlist()).contains("example.com");
        Assertions.assertThat(refreshed.isScreenshotEnabled()).isTrue();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldLetOwnerRenameViaUpdate() {
        // ARRANGE
        setupSharedCollection();

        // ACT
        collectionService.updateCollection(
            collectionId, "Owner Renamed", "example.com", true, owner, CollectionRole.OWNER);

        // ASSERT
        Assertions.assertThat(collectionService.getCollection(collectionId).getName()).isEqualTo("Owner Renamed");
    }
}
