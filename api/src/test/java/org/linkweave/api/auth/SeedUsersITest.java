package org.linkweave.api.auth;

import java.util.EnumSet;

import org.linkweave.api.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.auth.FachRolle;
import org.linkweave.api.shared.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SeedUsersITest {

    @Inject
    UserRepo userRepo;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"USER"})
    void shouldGrantAliceSupportRole() {
        User alice = userRepo.findByEmail(EmailAddress.fromString("alice@example.com")).orElseThrow();

        EnumSet<FachRolle> rollen = alice.getFachRollen();
        assertThat(rollen).contains(FachRolle.SUPPORT).contains(FachRolle.USER);
        assertThat(alice.getSecurityRoles()).contains("SUPPORT");
    }
}
