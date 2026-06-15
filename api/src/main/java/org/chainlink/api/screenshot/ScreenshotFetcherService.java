package org.chainlink.api.screenshot;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.collection.favicon.FaviconFetcherService;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.NoTransactionService;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@NoTransactionService
@Slf4j
@RequiredArgsConstructor
public class ScreenshotFetcherService {

    private static final int VIEWPORT_WIDTH = 1280;
    private static final int VIEWPORT_HEIGHT = 800;
    private static final int JPEG_QUALITY = 60;

    private final ConfigService configService;

    @RestClient
    ScreenshotSidecarClient screenshotSidecarClient;

    public @NonNull Optional<FetchedScreenshot> fetchFor(@NonNull URL pageUrl) {
        if (configService.getBackendFetchDenylist().matches(pageUrl.getHost())) {
            LOG.debug("Screenshot capture skipped for {} (matches backend fetch denylist)", pageUrl.getHost());
            return Optional.empty();
        }
        if (!FaviconFetcherService.isAllowedScheme(pageUrl.getProtocol())
            || !FaviconFetcherService.isPublicHost(pageUrl.getHost())) {
            return Optional.empty();
        }
        try (Response response = screenshotSidecarClient.capture(new ScreenshotSidecarClient.CaptureRequest(
            pageUrl.toString(), VIEWPORT_WIDTH, VIEWPORT_HEIGHT, "jpeg", JPEG_QUALITY))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                LOG.debug("Screenshot sidecar returned {} for {}", response.getStatus(), pageUrl);
                return Optional.empty();
            }
            byte[] bytes = response.readEntity(byte[].class);
            String description = decodeDescription(
                response.getHeaderString(ScreenshotSidecarClient.DESCRIPTION_HEADER));
            return Optional.of(new FetchedScreenshot(bytes, "image/jpeg", description));
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

    public record FetchedScreenshot(
        byte @NonNull [] bytes, @NonNull String contentType, @Nullable String description) {}
}
