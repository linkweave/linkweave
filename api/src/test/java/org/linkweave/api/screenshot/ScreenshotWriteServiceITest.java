package org.linkweave.api.screenshot;

import java.time.OffsetDateTime;

import org.linkweave.infrastructure.runas.RunAs;
import org.linkweave.api.types.id.ID;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.Test;

import static org.linkweave.api.shared.auth.BerechtigungName.SYSTEM_ADMIN;

/**
 * Regression test for the capture write running on the <em>anonymous</em>
 * scheduler thread.
 *
 * <p>Deliberately carries NO {@code @TestSecurity}: the test thread has no
 * logged-in user, exactly like the {@code @Scheduled} capture job. Without
 * {@link RunAs} on {@link ScreenshotWriteService#applyCapture}, the flush's
 * {@code AbstractEntityListener.preUpdate} (which stamps {@code userMutiert})
 * throws {@code AppAuthException} for the anonymous identity and the write is
 * silently rolled back — the exact bug this guards against. Fixtures are created
 * through a {@code @RunAs} helper so that <em>setup</em> doesn't itself trip the
 * anonymous-user guard, leaving the test method genuinely unauthenticated.
 */
@QuarkusTest
class ScreenshotWriteServiceITest {

    private static final OffsetDateTime CAPTURED_AT = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    @Inject
    ScreenshotWriteService writer;

    @Inject
    BookmarkRepo bookmarkRepo;

    @Inject
    SystemAdminFixture fixture;

    @Test
    void shouldPersistCaptureFromAnonymousThreadAndStampSystemAdmin() {
        // ARRANGE
        ID<Bookmark> bookmarkId = fixture.createBookmark(null);

        // No security context here — mirrors the scheduled capture job. Throws
        // (and rolls back) if applyCapture is not @RunAs system admin.
        // ACT
        writer.applyCapture(bookmarkId, CAPTURED_AT, "Scraped from the page");

        // ASSERT
        Bookmark reloaded = bookmarkRepo.getById(bookmarkId);
        Assertions.assertThat(reloaded.getScreenshotCapturedAt())
            .as("stamp persisted despite no logged-in user")
            .isEqualTo(CAPTURED_AT);
        Assertions.assertThat(reloaded.getDescription())
            .as("empty description backfilled")
            .isEqualTo("Scraped from the page");
        Assertions.assertThat(reloaded.getUserMutiert())
            .as("audit user is the system admin, via @RunAs")
            .isEqualTo(User.getSystemAdminId().getId());
    }

    @Test
    void shouldNotOverwriteAnExistingDescription() {
        // ARRANGE
        ID<Bookmark> bookmarkId = fixture.createBookmark("User wrote this");

        // ACT
        writer.applyCapture(bookmarkId, CAPTURED_AT, "Scraped from the page");

        // ASSERT
        Bookmark reloaded = bookmarkRepo.getById(bookmarkId);
        Assertions.assertThat(reloaded.getDescription())
            .as("user-supplied description is preserved")
            .isEqualTo("User wrote this");
        Assertions.assertThat(reloaded.getScreenshotCapturedAt())
            .as("stamp still persists")
            .isEqualTo(CAPTURED_AT);
    }

    /**
     * Creates fixtures as the system admin so setup doesn't trip the anonymous
     * guard the test under examination is meant to exercise.
     */
    @ApplicationScoped
    static class SystemAdminFixture {

        @Inject
        FixtureService fixtureService;

        @RunAs(username = "sysadmin", roles = {SYSTEM_ADMIN})
        ID<Bookmark> createBookmark(String description) {
            Collection collection = fixtureService.createTestCollection();
            return fixtureService.persistBookmark(b -> b
                .withCollection(collection)
                .withDescription(description)).getId();
        }
    }
}
