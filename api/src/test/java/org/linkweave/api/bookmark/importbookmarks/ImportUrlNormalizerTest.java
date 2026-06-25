package org.linkweave.api.bookmark.importbookmarks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportUrlNormalizerTest {

    @Test
    void shouldLowercaseSchemeAndHost() {
        assertThat(ImportUrlNormalizer.normalize("HTTPS://Example.COM/Path"))
            .isEqualTo("https://example.com/Path");
    }

    @Test
    void shouldStripTrailingSlashFromNonRootPath() {
        assertThat(ImportUrlNormalizer.normalize("https://example.com/path/"))
            .isEqualTo("https://example.com/path");
    }

    @Test
    void shouldTreatBareDomainAndRootSlashAsEqual() {
        // Parity with lib/url.ts: a lone root "/" is stripped, so the two
        // most common forms of a bare domain collapse to one.
        assertThat(ImportUrlNormalizer.normalize("https://example.com/"))
            .isEqualTo("https://example.com");
        assertThat(ImportUrlNormalizer.normalize("https://example.com"))
            .isEqualTo("https://example.com");
    }

    @Test
    void shouldDropFragment() {
        assertThat(ImportUrlNormalizer.normalize("https://example.com/x#section"))
            .isEqualTo("https://example.com/x");
    }

    @Test
    void shouldSortQueryParameters() {
        assertThat(ImportUrlNormalizer.normalize("https://example.com/x?b=2&a=1"))
            .isEqualTo("https://example.com/x?a=1&b=2");
    }

    @Test
    void shouldKeepUtmParams() {
        // Conservative parity with lib/url.ts — utm_* are NOT stripped.
        assertThat(ImportUrlNormalizer.normalize("https://example.com/x?utm_source=x"))
            .isEqualTo("https://example.com/x?utm_source=x");
    }

    @Test
    void shouldPreservePercentEncodingInQuery() {
        // Raw (encoded) query is kept — matches lib/url.ts and round-trips when
        // the same URL is re-imported (UC-096 dedup bug fix).
        String url = "https://auth.example.com/login?return_url=https%3A%2F%2Fauth.example.com%2Fx";
        assertThat(ImportUrlNormalizer.normalize(url)).isEqualTo(url);
    }

    @Test
    void shouldFallBackForMalformedInput() {
        assertThat(ImportUrlNormalizer.normalize("  Not A Url  "))
            .isEqualTo("not a url");
    }
}
