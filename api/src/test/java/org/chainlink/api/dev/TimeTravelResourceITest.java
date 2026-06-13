package org.chainlink.api.dev;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TimeTravelResourceITest {

    @Test
    void shouldReturnCurrentStatus() {
        RestAssured.given()
            .get("/dev/time-travel")
            .then()
            .statusCode(200)
            .body("timeTravelling", is(false))
            .body("now", notNullValue());
    }

    @Test
    void shouldTravelToGivenInstant() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"instant\":\"2099-01-01T00:00:00Z\"}")
            .post("/dev/time-travel")
            .then()
            .statusCode(200)
            .body("timeTravelling", is(true));

        // reset afterwards so other tests aren't affected
        RestAssured.given()
            .delete("/dev/time-travel")
            .then()
            .statusCode(200)
            .body("timeTravelling", is(false));
    }

    @Test
    void shouldReturn400_whenInstantMissing() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{}")
            .post("/dev/time-travel")
            .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400_whenInstantInvalid() {
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"instant\":\"not-a-date\"}")
            .post("/dev/time-travel")
            .then()
            .statusCode(400);
    }

    @Test
    void shouldResetClock() {
        // first travel
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body("{\"instant\":\"2099-06-01T12:00:00Z\"}")
            .post("/dev/time-travel")
            .then()
            .statusCode(200)
            .body("timeTravelling", is(true));

        // then reset
        RestAssured.given()
            .delete("/dev/time-travel")
            .then()
            .statusCode(200)
            .body("timeTravelling", is(false));
    }
}
