package org.linkweave.api.screenshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.shared.net.HostPatternSet;
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

    @Test
    void shouldDecodeBase64Utf8DescriptionHeader() {
        String original = "Café — résumé of the page ☕";
        String header = Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));

        assertThat(ScreenshotFetcherService.decodeDescription(header)).isEqualTo(original);
    }

    @Test
    void shouldReturnNullForMissingOrBlankDescriptionHeader() {
        assertThat(ScreenshotFetcherService.decodeDescription(null)).isNull();
        assertThat(ScreenshotFetcherService.decodeDescription("")).isNull();
        assertThat(ScreenshotFetcherService.decodeDescription("   ")).isNull();
        // base64 of an empty/whitespace string decodes to blank → treated as none
        assertThat(ScreenshotFetcherService.decodeDescription(
            Base64.getEncoder().encodeToString("   ".getBytes(StandardCharsets.UTF_8)))).isNull();
    }

    @Test
    void shouldReturnNullForMalformedBase64RatherThanThrow() {
        assertThat(ScreenshotFetcherService.decodeDescription("not valid base64 !@#")).isNull();
    }
}
