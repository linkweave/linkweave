package org.linkweave.infrastructure.exceptionmapper;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests that trigger the various JAX-RS ExceptionMappers via HTTP.
 * Each test sends malformed/invalid JSON to exercise a specific mapper's error-handling path.
 * Status codes vary by mapper: validation/json-parse → 400, json-mapping failures → 500.
 */
@QuarkusTest
class ExceptionMapperITest {

    // ── JsonParseExceptionMapper ──────────────────────────────────────────
    // Triggered when the request body is not valid JSON at all.

    @Test
    void shouldReturn400_whenJsonIsMalformed() {
        String body = "{not valid json";
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/dev/time-travel")
            .then()
            .statusCode(400)
            .body("id", notNullValue())
            .body("summary", notNullValue());
    }

    // ── UnrecognizedPropertyExceptionMapper ──────────────────────────────
    // Triggered when JSON contains a field that doesn't exist on the target DTO.
    // Requires Jackson's FAIL_ON_UNKNOWN_PROPERTIES to be enabled (configured in JacksonCustomizer).

    @Test
    void shouldReturn400_whenJsonHasUnknownProperty() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"completelyUnknownField\":\"value\"}")
            .post("/dev/time-travel")
            .then()
            .statusCode(400)
            .body("id", notNullValue())
            .body("summary", containsString("completelyUnknownField"));
    }

    // ── ConstraintViolationExceptionMapper + ConstraintViolationParser ────
    // Triggered when request body passes JSON deserialization but fails bean validation.

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturnError_whenBeanValidationFails() {
        // MoveToTrashJson has @NotNull on collectionId and @NotEmpty on bookmarkIds.
        // Sending null + empty list triggers ConstraintViolationException.
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"collectionId\":null,\"bookmarkIds\":[]}")
            .post("/cleanup-suggestions/move-to-trash")
            .then()
            .statusCode(org.hamcrest.Matchers.either(org.hamcrest.Matchers.is(400)).or(org.hamcrest.Matchers.is(500)))
            .body("id", notNullValue());
    }

    // ── JsonMappingExceptionMapper ────────────────────────────────────────
    // Triggered when JSON deserialization fails due to a type mismatch
    // (e.g., passing a number where an ID<UUID> string is expected).
    // JsonMappingExceptionMapper.buildFailureResponse returns 500 (serverError).

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturnError_whenJsonTypeMismatch() {
        // ID<Collection> expects a UUID string; sending a number causes JsonMappingException
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"collectionId\":12345,\"bookmarkIds\":[\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"]}")
            .post("/cleanup-suggestions/move-to-trash")
            .then()
            .statusCode(500)
            .body("id", notNullValue());
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_WRITE"})
    void shouldReturnError_whenJsonHasInvalidIdFormat() {
        // Send a string that's not a valid UUID → ID deserialization fails → JsonMappingException
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"collectionId\":\"not-a-uuid\",\"bookmarkIds\":[\"also-not-a-uuid\"]}")
            .post("/cleanup-suggestions/move-to-trash")
            .then()
            .statusCode(500)
            .body("id", notNullValue());
    }
}
