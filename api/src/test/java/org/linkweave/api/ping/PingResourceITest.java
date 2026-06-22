package org.linkweave.api.ping;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PingResourceITest {

    @Test
    void shouldRespondWithoutAuthentication() {
        RestAssured.given()
            .redirects().follow(false)
            .get("/ping")
            .then()
            .statusCode(204);
    }
}
