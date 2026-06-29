package org.linkweave.api.auth;

import java.util.UUID;

import org.linkweave.api.types.emailaddress.EmailAddress;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.linkweave.api.benutzer.UserRepo;
import org.linkweave.api.shared.user.AuthProvider;
import org.linkweave.api.shared.user.User;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class RegistrationServiceITest {

    @Inject
    RegistrationService registrationService;

    @Inject
    UserRepo userRepo;

    @Test
    @TestSecurity(user = "test@example.com", roles = {"USER"})
    void shouldRegisterNewUserWithHashedPassword() {
        // ARRANGE
        String email = uniqueEmail();

        // ACT
        User user = registrationService.register(email, "secret1234", "Max", "Mustermann");

        // ASSERT
        assertThat(user.getEmail()).isEqualTo(EmailAddress.fromString(email));
        assertThat(user.getVorname()).isEqualTo("Max");
        assertThat(user.getNachname()).isEqualTo("Mustermann");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.FORM);
        assertThat(user.getPassword()).isNotBlank();
        assertThat(user.getPassword()).isNotEqualTo("secret1234");
        assertThat(user.isAktiv()).isTrue();
        assertThat(user.getKeycloakId()).isNull();

        User persisted = userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow();
        assertThat(persisted.getPassword()).isEqualTo(user.getPassword());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"USER"})
    void shouldHashPasswordWithBcrypt() {
        // ARRANGE
        String email = uniqueEmail();
        String plainPassword = "my-secure-password!";

        // ACT
        registrationService.register(email, plainPassword, "Ada", "Lovelace");

        // ASSERT
        User persisted = userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow();
        assertThat(BcryptUtil.matches(plainPassword, persisted.getPassword())).isTrue();
        assertThat(BcryptUtil.matches("wrong-password", persisted.getPassword())).isFalse();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"USER"})
    void shouldRejectDuplicateEmail() {
        String email = uniqueEmail();
        registrationService.register(email, "password1", "First", "User");

        assertThatThrownBy(() -> registrationService.register(email, "password2", "Second", "User"))
            .isInstanceOf(AppValidationException.class)
            .hasMessageContaining("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"USER"})
    void shouldAssignUserRole() {
        // ARRANGE
        String email = uniqueEmail();

        // ACT
        User user = registrationService.register(email, "password123", "Jane", "Doe");

        // ASSERT
        assertThat(user.getFachRollen()).containsExactly(
            org.linkweave.api.shared.auth.FachRolle.USER
        );
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"USER"})
    void shouldPersistUserInDatabase() {
        // ARRANGE
        String email = uniqueEmail();

        // ACT
        registrationService.register(email, "password123", "Jane", "Doe");

        // ASSERT
        User found = userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow();
        assertThat(found.getVorname()).isEqualTo("Jane");
        assertThat(found.getNachname()).isEqualTo("Doe");
        assertThat(found.getAuthProvider()).isEqualTo(AuthProvider.FORM);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"USER"})
    void shouldNotStorePasswordInPlaintext() {
        // ARRANGE
        String email = uniqueEmail();
        String plainPassword = "super-secret-123";

        // ACT
        registrationService.register(email, plainPassword, "Test", "User");

        // ASSERT
        User persisted = userRepo.findByEmail(EmailAddress.fromString(email)).orElseThrow();
        assertThat(persisted.getPassword()).doesNotContain(plainPassword);
        assertThat(persisted.getPassword()).startsWith("$2");
    }

    private static String uniqueEmail() {
        return "svc-" + UUID.randomUUID() + "@test.com";
    }
}
