package org.chainlink.api.screenshot;

import java.net.URL;
import java.util.Optional;

import jakarta.ws.rs.WebApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.collection.favicon.FaviconFetcherService;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.Service;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.NonNull;

@Service
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
        if (configService.getFetchSkipList().matches(pageUrl.getHost())) {
            LOG.debug("Screenshot capture skipped for {} (matches fetch skip list)", pageUrl.getHost());
            return Optional.empty();
        }
        if (!FaviconFetcherService.isAllowedScheme(pageUrl.getProtocol())
            || !FaviconFetcherService.isPublicHost(pageUrl.getHost())) {
            return Optional.empty();
        }
        try {
            byte[] bytes = screenshotSidecarClient.capture(new ScreenshotSidecarClient.CaptureRequest(
                pageUrl.toString(), VIEWPORT_WIDTH, VIEWPORT_HEIGHT, "jpeg", JPEG_QUALITY));
            return Optional.of(new FetchedScreenshot(bytes, "image/jpeg"));
        } catch (WebApplicationException e) {
            LOG.debug("Screenshot sidecar returned {} for {}", e.getResponse().getStatus(), pageUrl);
            return Optional.empty();
        } catch (RuntimeException e) {
            // Network failures (connection refused, timeout, DNS) surface as
            // ProcessingException or similar from the REST Client runtime.
            LOG.debug("Screenshot fetch failed for {}: {}", pageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    public record FetchedScreenshot(byte @NonNull [] bytes, @NonNull String contentType) {}
}
