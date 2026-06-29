package org.linkweave.api.screenshot;

import static org.assertj.core.api.Assertions.assertThat;

import org.linkweave.infrastructure.db.DbConst;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure description-backfill decision. The capture flow itself
 * needs the sidecar and is exercised by {@link ScreenshotServiceITest}; here we
 * pin down the "only fill an empty field" rule and the truncation guard.
 */
class ScreenshotWriteServiceTest {

    @Test
    void shouldBackfillWhenExistingDescriptionIsNull() {
        assertThat(ScreenshotWriteService.descriptionToBackfill(null, "Scraped summary"))
            .isEqualTo("Scraped summary");
    }

    @Test
    void shouldBackfillWhenExistingDescriptionIsBlank() {
        assertThat(ScreenshotWriteService.descriptionToBackfill("   ", "Scraped summary"))
            .isEqualTo("Scraped summary");
    }

    @Test
    void shouldNotOverwriteAnExistingDescription() {
        assertThat(ScreenshotWriteService.descriptionToBackfill("User wrote this", "Scraped summary"))
            .isNull();
    }

    @Test
    void shouldNotBackfillWhenFetchedDescriptionIsMissing() {
        assertThat(ScreenshotWriteService.descriptionToBackfill(null, null)).isNull();
        assertThat(ScreenshotWriteService.descriptionToBackfill(null, "  ")).isNull();
    }

    @Test
    void shouldStripSurroundingWhitespaceFromFetchedDescription() {
        assertThat(ScreenshotWriteService.descriptionToBackfill(null, "  padded  "))
            .isEqualTo("padded");
    }

    @Test
    void shouldTruncateFetchedDescriptionToColumnLimit() {
        // ARRANGE
        String oversized = "x".repeat(DbConst.DB_TEXTAREA_MAX_LENGTH_5000 + 100);

        // ACT
        String result = ScreenshotWriteService.descriptionToBackfill(null, oversized);

        // ASSERT
        assertThat(result).hasSize(DbConst.DB_TEXTAREA_MAX_LENGTH_5000);
    }
}
