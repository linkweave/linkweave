package org.chainlink.api.screenshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.api.shared.net.HostPatternSet;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class ScreenshotFetcherServiceTest {

    @Test
    void shouldSkipCaptureForHostOnBackendFetchDenylist() throws Exception {
        // Skip check runs before the scheme/public-host guards and before the
        // sidecar client is touched, so a denylisted host returns empty even
        // though no sidecar client is wired in.
        ConfigService config = new ConfigService() {
            @Override
            @NonNull
            public HostPatternSet getBackendFetchDenylist() {
                return HostPatternSet.parse("blocked.example");
            }
        };
        ScreenshotFetcherService svc = new ScreenshotFetcherService(config);

        assertThat(svc.fetchFor(URI.create("https://blocked.example/page").toURL())).isEmpty();
    }
}
