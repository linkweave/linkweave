package org.chainlink.api.auth;

import java.util.UUID;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.config.RedirectConfig;
import jakarta.inject.Inject;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.shared.user.AuthProvider;
import org.chainlink.api.shared.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthResourceITest {

    @Inject
    CustomTestSecurityIdentityAugmentor augmentor;

    @Inject
    UserRepo userRepo;

    private final UUID uuid = UUID.randomUUID();
    private final UUID uuid2 = UUID.randomUUID();
    private final UUID uuid3 = UUID.randomUUID();

    @AfterEach
    void resetCustomTestSecurityIdentityAugmentor() {
        augmentor.setUuid(null);
    }

    @Test
    @TestSecurity(user = "user_1", augmentors = CustomTestSecurityIdentityAugmentor.class)
    @Order(1)
    void shouldEnsureUserCreation() {
        augmentor.setUuid(uuid);
        RestAssured.given()
            .config(RestAssured.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)))
            .get("/auth/oidc-login")
            .then()
            .statusCode(303);

        User user = userRepo.findByEmail(EmailAddress.fromString("email_" + uuid + "@example.com")).orElseThrow();
        assertThat(user.getEmail()).hasToString("email_" + uuid + "@example.com");
        assertThat(user.getVorname()).isEqualTo("gn" + uuid);
        assertThat(user.getNachname()).isEqualTo("fn_" + uuid);
        assertThat(user.getKeycloakId()).isEqualTo(uuid.toString());
        assertThat(user.isAktiv()).isTrue();
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.OIDC);
        assertThat(user.getPassword()).isNull();
    }

    @Test
    @TestSecurity(user = "user_2", augmentors = CustomTestSecurityIdentityAugmentor.class)
    @Order(2)
    void shouldEnsureUserCreation2() {
        augmentor.setUuid(uuid2);
        RestAssured.given()
            .config(RestAssured.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)))
            .get("/auth/oidc-login")
            .then()
            .statusCode(303);

        User user = userRepo.findByEmail(EmailAddress.fromString("email_" + uuid2 + "@example.com")).orElseThrow();
        assertThat(user.getEmail()).hasToString("email_" + uuid2 + "@example.com");
        assertThat(user.getVorname()).isEqualTo("gn" + uuid2);
        assertThat(user.getNachname()).isEqualTo("fn_" + uuid2);
        assertThat(user.getKeycloakId()).isEqualTo(uuid2.toString());
        assertThat(user.isAktiv()).isTrue();
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.OIDC);
    }

    @Test
    @TestSecurity(user = "user_3", augmentors = CustomTestSecurityIdentityAugmentor.class)
    @Order(3)
    void shouldEnsureUserCreation3() {
        augmentor.setUuid(uuid3);
        RestAssured.given()
            .config(RestAssured.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)))
            .get("/auth/oidc-login")
            .then()
            .statusCode(303);

        assertThat(userRepo.findByEmail(EmailAddress.fromString("email_" + uuid + "@example.com"))).isPresent();
        assertThat(userRepo.findByEmail(EmailAddress.fromString("email_" + uuid2 + "@example.com"))).isPresent();
        User user3 = userRepo.findByEmail(EmailAddress.fromString("email_" + uuid3 + "@example.com")).orElseThrow();
        assertThat(user3.getEmail()).hasToString("email_" + uuid3 + "@example.com");
        assertThat(user3.getVorname()).isEqualTo("gn" + uuid3);
        assertThat(user3.getNachname()).isEqualTo("fn_" + uuid3);
        assertThat(user3.getKeycloakId()).isEqualTo(uuid3.toString());
        assertThat(user3.isAktiv()).isTrue();
        assertThat(user3.getAuthProvider()).isEqualTo(AuthProvider.OIDC);
    }
}
