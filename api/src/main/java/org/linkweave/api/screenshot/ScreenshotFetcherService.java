package org.linkweave.api.screenshot;

import java.net.URL;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.collection.favicon.FaviconFetcherService;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.infrastructure.stereotypes.NoTransactionService;
import org.jspecify.annotations.NonNull;

/**
 * Decides whether a page may be captured at all, then delegates the capture to
 * {@link ScreenshotCapturer}.
 *
 * <p>The split is deliberate. The guards below are policy — the operator's
 * backend-fetch denylist, the scheme allowlist, and the private-host check that
 * keeps a bookmark URL from turning the backend into an SSRF proxy — and they
 * hold whatever the capture backend happens to be. Reaching the sidecar is
 * transport, and belongs to whichever class implements {@link ScreenshotCapturer}.
 */
@NoTransactionService
@Slf4j
@RequiredArgsConstructor
public class ScreenshotFetcherService {

    private final ConfigService configService;
    private final ScreenshotCapturer screenshotCapturer;

    public @NonNull Optional<ScreenshotCapturer.CapturedPage> fetchFor(@NonNull URL pageUrl) {
        if (configService.getBackendFetchDenylist().matches(pageUrl.getHost())) {
            LOG.debug("Screenshot capture skipped for {} (matches backend fetch denylist)", pageUrl.getHost());
            return Optional.empty();
        }
        if (!FaviconFetcherService.isAllowedScheme(pageUrl.getProtocol())
            || !FaviconFetcherService.isPublicHost(pageUrl.getHost())) {
            return Optional.empty();
        }
        return screenshotCapturer.capture(pageUrl);
    }
}
