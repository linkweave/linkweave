package org.linkweave.api.screenshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.shared.net.HostPatternSet;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Policy tests. With capture behind {@link ScreenshotCapturer} these run against
 * a fake and need no sidecar, so the allowed-host path is assertable here and not
 * only the branches that return before any call is made.
 *
 * <p>Hosts are IP literals rather than names so {@code InetAddress.getAllByName}
 * resolves them without a DNS lookup: these stay deterministic offline.
 */
class ScreenshotFetcherServiceTest {

    @Test
    void shouldSkipCaptureForHostOnBackendFetchDenylist() throws Exception {
        // ARRANGE
        RecordingCapturer capturer = new RecordingCapturer(Optional.empty());
        ScreenshotFetcherService svc =
            new ScreenshotFetcherService(configDenying("blocked.example"), capturer);

        // ACT
        Optional<ScreenshotCapturer.CapturedPage> result =
            svc.fetchFor(URI.create("https://blocked.example/page").toURL());

        // ASSERT
        assertThat(result).isEmpty();
        assertThat(capturer.calls).as("denylisted host must not reach the capturer").isZero();
    }

    @Test
    void shouldSkipCaptureForNonPublicHost() throws Exception {
        // ARRANGE — loopback: the SSRF guard, not the denylist, is what rejects this
        RecordingCapturer capturer = new RecordingCapturer(Optional.empty());
        ScreenshotFetcherService svc =
            new ScreenshotFetcherService(configDenying("blocked.example"), capturer);

        // ACT
        Optional<ScreenshotCapturer.CapturedPage> result =
            svc.fetchFor(URI.create("https://127.0.0.1/page").toURL());

        // ASSERT
        assertThat(result).isEmpty();
        assertThat(capturer.calls).as("private host must not reach the capturer").isZero();
    }

    @Test
    void shouldSkipCaptureForDisallowedScheme() throws Exception {
        // ARRANGE — a public host, so only the scheme guard can reject this one
        RecordingCapturer capturer = new RecordingCapturer(Optional.empty());
        ScreenshotFetcherService svc =
            new ScreenshotFetcherService(configDenying("blocked.example"), capturer);

        // ACT
        Optional<ScreenshotCapturer.CapturedPage> result =
            svc.fetchFor(URI.create("ftp://8.8.8.8/page").toURL());

        // ASSERT
        assertThat(result).isEmpty();
        assertThat(capturer.calls).as("non-http scheme must not reach the capturer").isZero();
    }

    @Test
    void shouldReturnWhatTheCapturerProducedForAnAllowedHost() throws Exception {
        // ARRANGE
        ScreenshotCapturer.CapturedPage page = new ScreenshotCapturer.CapturedPage(
            "image-bytes".getBytes(StandardCharsets.UTF_8), "image/jpeg", "a page description");
        RecordingCapturer capturer = new RecordingCapturer(Optional.of(page));
        ScreenshotFetcherService svc =
            new ScreenshotFetcherService(configDenying("blocked.example"), capturer);
        URL allowed = URI.create("https://8.8.8.8/page").toURL();

        // ACT
        Optional<ScreenshotCapturer.CapturedPage> result = svc.fetchFor(allowed);

        // ASSERT
        assertThat(result).contains(page);
        assertThat(capturer.lastUrl).isEqualTo(allowed);
    }

    private static @NonNull ConfigService configDenying(@NonNull String patterns) {
        return new ConfigService() {
            @Override
            public @NonNull HostPatternSet getBackendFetchDenylist() {
                return HostPatternSet.parse(patterns);
            }
        };
    }

    /** Fake capture backend: records that it was reached, and with which URL. */
    private static final class RecordingCapturer implements ScreenshotCapturer {

        private final Optional<CapturedPage> result;
        private int calls;
        private @Nullable URL lastUrl;

        private RecordingCapturer(@NonNull Optional<CapturedPage> result) {
            this.result = result;
        }

        @Override
        public @NonNull Optional<CapturedPage> capture(@NonNull URL pageUrl) {
            calls++;
            lastUrl = pageUrl;
            return result;
        }
    }
}
