package org.chainlink.api.screenshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.api.shared.net.HostSkipList;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class ScreenshotFetcherServiceTest {

    @Test
    void shouldSkipCaptureForHostOnGlobalSkipList() throws Exception {
        // Skip check runs before the scheme/public-host guards and before the
        // sidecar client is touched, so a skip-listed host returns empty even
        // though no sidecar client is wired in.
        ConfigService config = new ConfigService() {
            @Override
            @NonNull
            public HostSkipList getFetchSkipList() {
                return HostSkipList.parse("blocked.example");
            }
        };
        ScreenshotFetcherService svc = new ScreenshotFetcherService(config);

        assertThat(svc.fetchFor(URI.create("https://blocked.example/page").toURL())).isEmpty();
    }
}
