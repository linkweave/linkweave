package org.chainlink.api.screenshot;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.UUID;

import ch.dvbern.dvbstarter.clock.ClockProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.chainlink.api.shared.config.ConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ScreenshotCacheServiceITest {

    @Inject
    ScreenshotCacheService cache;

    @Inject
    ClockProvider clockProvider;

    @Inject
    ConfigService configService;

    @AfterEach
    void resetClock() {
        clockProvider.reset();
    }

    @Test
    void shouldRoundTripSuccessEntry() throws Exception {
        URL url = URI.create("https://example.com/round-trip-" + UUID.randomUUID()).toURL();
        String key = ScreenshotCacheService.keyFor(url);
        byte[] payload = "fake-jpeg".getBytes();

        cache.putSuccess(key, payload, "image/jpeg");

        try {
            var loaded = cache.get(key);
            Assertions.assertThat(loaded).isPresent();
            Assertions.assertThat(loaded.get().bytes()).isEqualTo(payload);
            Assertions.assertThat(loaded.get().contentType()).isEqualTo("image/jpeg");
            Assertions.assertThat(loaded.get().negative()).isFalse();
        } finally {
            cache.deleteForKey(key);
        }
    }

    @Test
    void shouldReturnNegativeFlagForNegativeEntry() throws Exception {
        URL url = URI.create("https://example.com/negative-" + UUID.randomUUID()).toURL();
        String key = ScreenshotCacheService.keyFor(url);

        cache.putNegative(key);

        try {
            var loaded = cache.get(key);
            Assertions.assertThat(loaded).isPresent();
            Assertions.assertThat(loaded.get().negative()).isTrue();
            Assertions.assertThat(loaded.get().bytes()).isEmpty();
        } finally {
            cache.deleteForKey(key);
        }
    }

    @Test
    void shouldExpireSuccessEntryAfterSuccessTtl() throws Exception {
        URL url = URI.create("https://example.com/expire-success-" + UUID.randomUUID()).toURL();
        String key = ScreenshotCacheService.keyFor(url);

        cache.putSuccess(key, new byte[]{1, 2, 3}, "image/jpeg");
        try {
            Assertions.assertThat(cache.get(key)).isPresent();

            Instant pastTtl = Instant.now()
                .plus(configService.getScreenshotSuccessTtl())
                .plusSeconds(60);
            clockProvider.resetUsing(pastTtl);

            Assertions.assertThat(cache.get(key)).isEmpty();
        } finally {
            cache.deleteForKey(key);
        }
    }

    @Test
    void shouldExpireNegativeEntryAfterNegativeTtl() throws Exception {
        URL url = URI.create("https://example.com/expire-negative-" + UUID.randomUUID()).toURL();
        String key = ScreenshotCacheService.keyFor(url);

        cache.putNegative(key);
        try {
            Assertions.assertThat(cache.get(key)).isPresent();

            Instant pastNegTtl = Instant.now()
                .plus(configService.getScreenshotNegativeTtl())
                .plusSeconds(60);
            clockProvider.resetUsing(pastNegTtl);

            Assertions.assertThat(cache.get(key)).isEmpty();
        } finally {
            cache.deleteForKey(key);
        }
    }

    @Test
    void shouldEscalateNegativeTtlOnConsecutiveFailures() throws Exception {
        URL url = URI.create("https://example.com/escalate-" + UUID.randomUUID()).toURL();
        String key = ScreenshotCacheService.keyFor(url);
        long baseSeconds = configService.getScreenshotNegativeTtl().toSeconds();

        try {
            // First failure: entry lives for the base TTL and expires just past it.
            Instant t0 = Instant.now();
            clockProvider.resetUsing(t0);
            cache.putNegative(key);
            clockProvider.resetUsing(t0.plusSeconds(baseSeconds + 60));
            Assertions.assertThat(cache.get(key))
                .as("first failure expires at the base negative TTL")
                .isEmpty();

            // Second failure (recorded after the first expired): TTL must double,
            // so an age just over the base TTL is now still within the window.
            Instant t1 = t0.plusSeconds(baseSeconds + 60);
            clockProvider.resetUsing(t1);
            cache.putNegative(key);
            clockProvider.resetUsing(t1.plusSeconds(baseSeconds + 60));
            Assertions.assertThat(cache.get(key))
                .as("second failure doubled the TTL → still cached past the base window")
                .isPresent();
            Assertions.assertThat(cache.get(key).get().negative()).isTrue();

            // Past the doubled window it finally expires.
            clockProvider.resetUsing(t1.plusSeconds(2 * baseSeconds + 120));
            Assertions.assertThat(cache.get(key))
                .as("escalated entry expires once the doubled TTL elapses")
                .isEmpty();
        } finally {
            cache.deleteForKey(key);
        }
    }

    @Test
    void shouldResetBackoffAfterSuccess() throws Exception {
        URL url = URI.create("https://example.com/reset-backoff-" + UUID.randomUUID()).toURL();
        String key = ScreenshotCacheService.keyFor(url);
        long baseSeconds = configService.getScreenshotNegativeTtl().toSeconds();

        try {
            Instant t0 = Instant.now();
            clockProvider.resetUsing(t0);
            cache.putNegative(key); // failure 1
            clockProvider.resetUsing(t0.plusSeconds(baseSeconds + 60));
            cache.putNegative(key); // failure 2 → TTL doubled

            // A success clears the failure streak (its meta is non-negative, so
            // the next putNegative reads no prior failure count).
            Instant tSuccess = t0.plusSeconds(baseSeconds + 120);
            clockProvider.resetUsing(tSuccess);
            cache.putSuccess(key, new byte[]{1}, "image/jpeg");

            // The next failure starts over at the base TTL, not the escalated one.
            Instant t1 = tSuccess.plusSeconds(10);
            clockProvider.resetUsing(t1);
            cache.putNegative(key);
            clockProvider.resetUsing(t1.plusSeconds(baseSeconds + 60));
            Assertions.assertThat(cache.get(key))
                .as("after a success, backoff resets to the base TTL")
                .isEmpty();
        } finally {
            cache.deleteForKey(key);
        }
    }

    @Test
    void shouldKeyTheSameForUrlsThatDifferOnlyInFragment() throws Exception {
        URL withFragment = URI.create("https://example.com/page?q=1#section").toURL();
        URL withoutFragment = URI.create("https://example.com/page?q=1").toURL();

        Assertions.assertThat(ScreenshotCacheService.keyFor(withFragment))
            .isEqualTo(ScreenshotCacheService.keyFor(withoutFragment));
    }

    @Test
    void shouldKeyDifferentlyForUrlsThatDifferInQuery() throws Exception {
        URL a = URI.create("https://example.com/page?q=1").toURL();
        URL b = URI.create("https://example.com/page?q=2").toURL();

        Assertions.assertThat(ScreenshotCacheService.keyFor(a))
            .isNotEqualTo(ScreenshotCacheService.keyFor(b));
    }

    @Test
    void shouldNormalizeHostCase() throws Exception {
        URL upper = URI.create("https://EXAMPLE.com/").toURL();
        URL lower = URI.create("https://example.com/").toURL();

        Assertions.assertThat(ScreenshotCacheService.keyFor(upper))
            .isEqualTo(ScreenshotCacheService.keyFor(lower));
    }

    @Test
    void shouldReportBytesFreedOnDelete() throws Exception {
        URL url = URI.create("https://example.com/freed-" + UUID.randomUUID()).toURL();
        String key = ScreenshotCacheService.keyFor(url);
        byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        cache.putSuccess(key, payload, "image/jpeg");
        long freed = cache.deleteForKey(key);

        Assertions.assertThat(freed).isGreaterThanOrEqualTo(payload.length);
        Assertions.assertThat(cache.get(key)).isEmpty();
    }
}
