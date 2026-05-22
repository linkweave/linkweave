package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.shared.user.User;
import org.chainlink.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;

@QuarkusTest
class CollectionAccessRepoITest {

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    FixtureService fixtureService;

    @Inject
    UserRepo userRepo;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotThrowWhenCountingSharedCollectionsOnExistingData() {
        assertThatCode(() -> collectionAccessRepo.countSharedCollections())
            .doesNotThrowAnyException();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldReturnNonNegativeCountAfterCreatingSharedCollection() {
        User owner = userRepo.findByEmail(EmailAddress.fromString("test@example.com")).orElseThrow();
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();

        long before = collectionAccessRepo.countSharedCollections();

        Collection shared = fixtureService.persistCollection(b -> b
            .withOwner(owner)
            .withName("Shared Collection")
        );
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(shared)
            .withUser(owner)
            .withRole(CollectionRole.OWNER)
            .withDefault(false)
        );
        fixtureService.persistCollectionAccess(b -> b
            .withCollection(shared)
            .withUser(alice)
            .withRole(CollectionRole.MEMBER)
            .withDefault(false)
        );

        long after = collectionAccessRepo.countSharedCollections();

        assertThat(after).isNotNegative();
        assertThat(after).isGreaterThanOrEqualTo(before);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldHandleMultipleSharedCollections() {
        User owner = userRepo.findByEmail(EmailAddress.fromString("test@example.com")).orElseThrow();
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();

        for (int i = 0; i < 3; i++) {
            final String name = "Shared " + i;
            Collection col = fixtureService.persistCollection(b -> b
                .withOwner(owner)
                .withName(name)
            );
            fixtureService.persistCollectionAccess(b -> b
                .withCollection(col)
                .withUser(owner)
                .withRole(CollectionRole.OWNER)
                .withDefault(false)
            );
            fixtureService.persistCollectionAccess(b -> b
                .withCollection(col)
                .withUser(alice)
                .withRole(CollectionRole.MEMBER)
                .withDefault(false)
            );
        }

        assertThatNoException().isThrownBy(() -> collectionAccessRepo.countSharedCollections());

        long result = collectionAccessRepo.countSharedCollections();
        assertThat(result).isNotNegative();
    }
}
