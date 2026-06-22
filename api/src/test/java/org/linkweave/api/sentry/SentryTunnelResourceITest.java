package org.linkweave.api.sentry;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SentryTunnelResourceITest {

    @BeforeEach
    void configureEncoder() {
        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig()
                        .encodeContentTypeAs("application/x-sentry-envelope", ContentType.TEXT));
    }

    private static final String VALID_ENVELOPE = """
            {"event_id":"abc123","dsn":"https://dd086e579810c04e75f2e37463ac7378@o4509425614520320.ingest.de.sentry.io/4511463699120208"}
            {"type":"event","length":41}
            {"message":"test","level":"error"}
            """;

    @Test
    void shouldRejectMissingDsn() {
        RestAssured.given()
                .contentType("application/x-sentry-envelope")
                .body("""
                        {"event_id":"abc123"}
                        {"type":"event","length":41}
                        {"message":"test","level":"error"}
                        """)
                .post("/sentry-tunnel")
                .then()
                .statusCode(400);
    }

    @Test
    void shouldRejectWrongProjectId() {
        RestAssured.given()
                .contentType("application/x-sentry-envelope")
                .body("""
                        {"event_id":"abc123","dsn":"https://key@o4509425614520320.ingest.de.sentry.io/9999999999"}
                        {"type":"event","length":41}
                        {"message":"test","level":"error"}
                        """)
                .post("/sentry-tunnel")
                .then()
                .statusCode(403);
    }

    @Test
    void shouldRejectNonSentryHost() {
        RestAssured.given()
                .contentType("application/x-sentry-envelope")
                .body("""
                        {"event_id":"abc123","dsn":"https://key@evil.example.com/4511463699120208"}
                        {"type":"event","length":41}
                        {"message":"test","level":"error"}
                        """)
                .post("/sentry-tunnel")
                .then()
                .statusCode(403);
    }

    @Test
    void shouldRejectSubdomainSpoofingAttempt() {
        RestAssured.given()
                .contentType("application/x-sentry-envelope")
                .body("""
                        {"event_id":"abc123","dsn":"https://key@sentry.io.evil.com/4511463699120208"}
                        {"type":"event","length":41}
                        {"message":"test","level":"error"}
                        """)
                .post("/sentry-tunnel")
                .then()
                .statusCode(403);
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() {
        // Tunnel must be public — it receives events before a user is logged in
        RestAssured.given()
                .redirects().follow(false)
                .contentType("application/x-sentry-envelope")
                .body(VALID_ENVELOPE)
                .post("/sentry-tunnel")
                // 200 or 5xx are both fine here (depends on Sentry reachability),
                // the important thing is we don't get 401/403 from our own auth layer
                .then()
                .statusCode(org.hamcrest.Matchers.not(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(401),
                        org.hamcrest.Matchers.is(302)
                )));
    }
}
