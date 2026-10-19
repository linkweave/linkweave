package org.linkweave.api.screenshot;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The production {@link ScreenshotCapturer}: the Playwright sidecar, reached over
 * HTTP through {@link ScreenshotSidecarClient}.
 *
 * <p>This is the only class permitted to see the sidecar's transport types. It
 * owns the viewport and encoding parameters, flattens a non-2xx status and every
 * transport failure into an empty result, and decodes the base64 description
 * header. Everything it translates away is something the rest of the application
 * would otherwise have had to understand.
 */
@ApplicationScoped
@Slf4j
public class SidecarScreenshotCapturer implements ScreenshotCapturer {

    private static final int VIEWPORT_WIDTH = 1280;
    private static final int VIEWPORT_HEIGHT = 800;
    private static final String IMAGE_FORMAT = "jpeg";
    private static final int JPEG_QUALITY = 60;

    @RestClient
    ScreenshotSidecarClient screenshotSidecarClient;

    @Override
    public @NonNull Optional<CapturedPage> capture(@NonNull URL pageUrl) {
        try (Response response = screenshotSidecarClient.capture(new ScreenshotSidecarClient.CaptureRequest(
            pageUrl.toString(), VIEWPORT_WIDTH, VIEWPORT_HEIGHT, IMAGE_FORMAT, JPEG_QUALITY))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                LOG.debug("Screenshot sidecar returned {} for {}", response.getStatus(), pageUrl);
                return Optional.empty();
            }
            byte[] bytes = response.readEntity(byte[].class);
            String description = decodeDescription(
                response.getHeaderString(ScreenshotSidecarClient.DESCRIPTION_HEADER));
            return Optional.of(new CapturedPage(bytes, "image/jpeg", description));
        } catch (RuntimeException e) {
            // Network failures (connection refused, timeout, DNS) surface as
            // ProcessingException or similar from the REST Client runtime.
            LOG.debug("Screenshot fetch failed for {}: {}", pageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Decodes the base64 (UTF-8) page description the sidecar returns in its
     * response header. A missing or malformed value is treated as "no
     * description" rather than failing the capture — the image is the product;
     * the description is a best-effort backfill.
     */
    static @Nullable String decodeDescription(@Nullable String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(headerValue.trim()), StandardCharsets.UTF_8);
            return decoded.isBlank() ? null : decoded;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
