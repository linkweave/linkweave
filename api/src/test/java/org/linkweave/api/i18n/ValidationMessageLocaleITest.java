package org.linkweave.api.i18n;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Bean-validation messages must follow the request's Accept-Language.
 *
 * Hibernate Validator only pre-initialises the locales it finds a
 * ValidationMessages_<lang>.properties bundle for; a request resolving to an
 * unregistered locale fails with HV000250 (HTTP 500). These tests pin that every
 * locale in quarkus.locales (en, de, fr, it) is registered and that the
 * app-specific @Pattern message is translated — so deleting a bundle file or a
 * locale regression is caught here rather than in production.
 */
@QuarkusTest
class ValidationMessageLocaleITest {

    private io.restassured.response.ValidatableResponse postCollection(String acceptLang) {
        return RestAssured.given()
            .contentType("application/json")
            .header("Accept-Language", acceptLang)
            .body("""
                {"name":""}
                """)
            .post("/collections")
            .then();
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldLocalizeStandardConstraintMessagePerAcceptLanguage() {
        postCollection("en").statusCode(400)
            .body("violations[0].message", equalTo("must not be blank"));
        postCollection("de").statusCode(400)
            .body("violations[0].message", containsString("darf nicht leer"));
        postCollection("fr").statusCode(400)
            .body("violations[0].message", containsString("ne doit pas être vide"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldNotFailWithHv000250ForAnyDeclaredLocale() {
        // it and en are the locales that previously threw HV000250 because no
        // ValidationMessages bundle registered them. Guard against regression.
        postCollection("en-US").statusCode(400);
        postCollection("it").statusCode(400);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldLocalizeAppSpecificPatternMessage() {
        RestAssured.given()
            .contentType("application/json")
            .header("Accept-Language", "fr")
            .body("""
                {"name":"k","expiresIn":"bogus"}
                """)
            .post("/auth/api-keys")
            .then()
            .statusCode(400)
            .body("violations[0].key", equalTo("Pattern"))
            .body("violations[0].message", equalTo("Doit être 30d, 90d, 1y ou never."));
    }
}
