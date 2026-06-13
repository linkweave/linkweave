package org.chainlink.api.collection.favicon;

import java.nio.file.Path;
import java.util.Optional;

import ch.dvbern.dvbstarter.clock.AppClock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.shared.cache.SidecarFileCache;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

/**
 * Per-origin favicon cache. Thin domain wrapper over {@link SidecarFileCache};
 * the on-disk file-pair mechanics, TTL handling, atomic writes and SHA-256
 * keying all live in the shared cache class.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FaviconCacheService {

    private final AppClock appClock;
    private final ConfigService configService;

    private SidecarFileCache cache;

    @PostConstruct
    void init() {
        cache = new SidecarFileCache(
            Path.of(configService.getFaviconCacheDir()),
            configService.getFaviconSuccessTtl(),
            configService.getFaviconNegativeTtl(),
            configService.getFaviconNegativeTtlMax(),
            appClock, "favicon", LOG);
    }

    public @NonNull Optional<CachedFavicon> get(@NonNull String origin) {
        return cache.get(origin).map(e -> new CachedFavicon(e.bytes(), e.contentType(), e.negative()));
    }

    public void putSuccess(@NonNull String origin, byte @NonNull [] bytes, @NonNull String contentType) {
        cache.putSuccess(origin, bytes, contentType);
    }

    public void putNegative(@NonNull String origin) {
        cache.putNegative(origin);
    }

    public long deleteForOrigin(@NonNull String origin) {
        return cache.delete(origin);
    }

    public @NonNull Path getCacheDir() {
        return cache.getCacheDir();
    }

    // Retained so cleanup tests can compose on-disk paths the same way the
    // cache does. Forwards to the shared helper.
    static @NonNull String sha256(@NonNull String input) {
        return SidecarFileCache.sha256(input);
    }

    public record CachedFavicon(byte @NonNull [] bytes, @NonNull String contentType, boolean negative) {}
}
