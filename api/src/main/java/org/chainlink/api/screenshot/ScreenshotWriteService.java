package org.chainlink.api.screenshot;

import java.time.OffsetDateTime;

import ch.dvbern.dvbstarter.runas.RunAs;
import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.infrastructure.db.DbConst;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static org.chainlink.api.shared.auth.BerechtigungName.SYSTEM_ADMIN;

/**
 * Transactional sink for a successful screenshot capture. Split out from
 * {@link ScreenshotService} (which runs without a transaction so the blocking
 * sidecar call doesn't hold a DB connection across its network round-trip) so
 * this DB mutation commits in its own short transaction — crucially {@link RunAs}
 * the system admin, since the scheduled job is anonymous.
 */
@Service
@RequiredArgsConstructor
public class ScreenshotWriteService {

    private final BookmarkRepo bookmarkRepo;

    /**
     * Stamps {@code screenshotCapturedAt} and, when the bookmark has no
     * description of its own yet, backfills the one the sidecar parsed from the
     * page during capture.
     *
     * <p>Runs as the system admin: the capture job has no logged-in user, so the
     * entity flush's {@code AbstractEntityListener.preUpdate} would otherwise
     * throw {@code AppAuthException} (anonymous) when stamping {@code userMutiert}
     * — silently rolling back the write. {@link RunAs} installs a SYSTEM_ADMIN
     * identity for the duration (and its commit), so the audit columns get the
     * system-admin id.
     */
    @RunAs(username = "sysadmin", roles = {SYSTEM_ADMIN})
    public void applyCapture(
        @NonNull ID<Bookmark> bookmarkId,
        @NonNull OffsetDateTime capturedAt,
        @Nullable String fetchedDescription
    ) {
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        bookmark.setScreenshotCapturedAt(capturedAt);
        String backfill = descriptionToBackfill(bookmark.getDescription(), fetchedDescription);
        if (backfill != null) {
            bookmark.setDescription(backfill);
        }
    }

    /**
     * Decides what, if anything, to write into a bookmark's description from a
     * freshly captured page. Returns the value to set, or {@code null} to leave
     * the description untouched.
     *
     * <p>Only fills an <em>empty</em> field — a user- or import-supplied
     * description always wins over the scraped one. The result is truncated to
     * the column limit so an oversized meta tag can't fail the flush.
     */
    static @Nullable String descriptionToBackfill(@Nullable String existing, @Nullable String fetched) {
        if (existing != null && !existing.isBlank()) {
            return null; // already has one — never overwrite
        }
        if (fetched == null || fetched.isBlank()) {
            return null; // nothing to fill it with
        }
        String trimmed = fetched.strip();
        return trimmed.length() > DbConst.DB_TEXTAREA_MAX_LENGTH_5000
            ? trimmed.substring(0, DbConst.DB_TEXTAREA_MAX_LENGTH_5000)
            : trimmed;
    }
}
