package org.linkweave.api.screenshot;

import java.net.URL;
import java.util.Optional;

import org.linkweave.infrastructure.stereotypes.Port;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Abstraction over capturing a rendered screenshot of a page. Kept as an
 * interface so {@link ScreenshotFetcherService} can be tested without the
 * Playwright sidecar running (swap in a fake), and so the capture backend can
 * change without touching the policy that decides what may be captured at all.
 *
 * <p>Deliberately free of transport vocabulary. {@link ScreenshotSidecarClient}
 * speaks HTTP and returns {@code jakarta.ws.rs.core.Response} so the adapter can
 * read both the image bytes and the description header. Nothing on this side of
 * the seam needs to know that, or to drain a JAX-RS response.
 *
 * <p>Capture parameters — viewport, format, quality — are the implementation's
 * business rather than the caller's, for the same reason {@link
 * org.linkweave.api.autotag.llm.LlmTaggingClient} reads its model and credentials
 * from config: passing them through this seam would put the backend's vocabulary
 * straight back into the signature.
 */
@Port
public interface ScreenshotCapturer {

    /**
     * Captures {@code pageUrl}, or returns empty when the page could not be
     * captured for any reason — backend unreachable, non-2xx, timeout, or a reply
     * that could not be read.
     *
     * <p>Does not throw: a missing screenshot is a normal outcome that callers
     * already handle (by writing a negative cache entry), not an exception.
     *
     * <p>Performs no policy check. Deciding whether {@code pageUrl} may be fetched
     * at all — operator denylist, scheme, private-host protection — belongs to the
     * caller, because that decision is the same whatever the capture backend is.
     */
    @NonNull
    Optional<CapturedPage> capture(@NonNull URL pageUrl);

    /**
     * A successful capture: the image bytes, their media type, and the page
     * description when the backend managed to extract one.
     */
    record CapturedPage(
        byte @NonNull [] bytes, @NonNull String contentType, @Nullable String description) {}
}
