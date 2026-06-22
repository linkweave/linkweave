package org.linkweave.api.shared.metrics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.linkweave.infrastructure.db.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class ApplicationMetricsITest {

    @Inject
    ApplicationMetricsService metricsService;

    @Inject
    FixtureService fixtureService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    void resetDatabase() {
        databaseService.resetDatabase();
    }

    @Test
    void shouldExposePrometheusMetricsEndpoint() {
        RestAssured.given()
            .basePath("/q")
            .get("/metrics")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldIncludeCustomLinkweaveMetricsAfterRefresh() {
        fixtureService.createTestCollection();

        metricsService.refreshMetrics();

        RestAssured.given()
            .basePath("/q")
            .get("/metrics")
            .then()
            .statusCode(200)
            .body(containsString("linkweave_collections_total"))
            .body(containsString("linkweave_collections_shared"))
            .body(containsString("linkweave_bookmarks_total"));
    }

    @Test
    @TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
    void shouldRefreshCollectionCountAfterCreation() {
        metricsService.refreshMetrics();
        long countBefore = countFromMetrics("linkweave_collections_total");

        fixtureService.createTestCollection();
        fixtureService.createTestCollection();

        metricsService.refreshMetrics();
        long countAfter = countFromMetrics("linkweave_collections_total");
        org.assertj.core.api.Assertions.assertThat(countAfter).isEqualTo(countBefore + 2);
    }

    private long countFromMetrics(String metricName) {
        String body = RestAssured.given()
            .basePath("/q")
            .get("/metrics")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
        return java.util.regex.Pattern.compile("\\b" + metricName + "(?:\\{[^}]*\\})? ([\\d.]+)")
            .matcher(body)
            .results()
            .mapToLong(m -> (long) Double.parseDouble(m.group(1)))
            .sum();
    }
}
