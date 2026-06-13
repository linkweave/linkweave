package org.chainlink.api.collection.favicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.api.shared.net.HostPatternSet;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure icon-link resolution that the redirect fix relies on:
 * {@code fetchFor} now threads the *final* URL (after redirects) into
 * {@link FaviconFetcherService#parseIconLink} as the base, so relative icon
 * hrefs resolve against the host we actually landed on rather than the
 * pre-redirect origin.
 */
class FaviconFetcherServiceTest {

    @Test
    void shouldResolveRootRelativeHrefAgainstRedirectedHost() {
        // Bookmark origin was https://example.com, but the request redirected to
        // www.example.com — the base passed in is the final URL.
        URI base = URI.create("https://www.example.com/home");
        String html = "<head><link rel=\"icon\" href=\"/static/icon.png\"></head>";

        URI icon = FaviconFetcherService.parseIconLink(html, base);

        assertThat(icon).isEqualTo(URI.create("https://www.example.com/static/icon.png"));
    }

    @Test
    void shouldResolveDocumentRelativeHrefAgainstRedirectedPath() {
        URI base = URI.create("https://www.example.com/blog/");
        String html = "<head><link rel=\"icon\" href=\"favicon.png\"></head>";

        URI icon = FaviconFetcherService.parseIconLink(html, base);

        assertThat(icon).isEqualTo(URI.create("https://www.example.com/blog/favicon.png"));
    }

    @Test
    void shouldPreferSvgIconOverLargerRasterIcon() {
        URI base = URI.create("https://example.com");
        String html = """
            <head>
              <link rel="icon" type="image/png" sizes="180x180" href="/big.png">
              <link rel="icon" type="image/svg+xml" href="/icon.svg">
            </head>
            """;

        URI icon = FaviconFetcherService.parseIconLink(html, base);

        assertThat(icon).isEqualTo(URI.create("https://example.com/icon.svg"));
    }

    @Test
    void shouldReturnNullWhenNoIconLinkPresent() {
        URI base = URI.create("https://example.com");
        String html = "<head><link rel=\"stylesheet\" href=\"/site.css\"></head>";

        assertThat(FaviconFetcherService.parseIconLink(html, base)).isNull();
    }

    @Test
    void shouldSkipFetchForHostOnBackendFetchDenylist() throws Exception {
        // Skip check runs before any DNS/network, so a never-resolving host
        // returns empty without touching the wire.
        FaviconFetcherService svc = new FaviconFetcherService(denylistConfig("*.internal.example"));

        assertThat(svc.fetchFor(URI.create("https://wiki.internal.example/").toURL())).isEmpty();
    }

    private static ConfigService denylistConfig(String denyDomains) {
        return new ConfigService() {
            @Override
            @NonNull
            public HostPatternSet getBackendFetchDenylist() {
                return HostPatternSet.parse(denyDomains);
            }
        };
    }
}
