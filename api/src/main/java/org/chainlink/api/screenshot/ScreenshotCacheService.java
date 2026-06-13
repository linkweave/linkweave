package org.chainlink.api.screenshot;

import java.net.URL;
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
 * Per-bookmark-URL screenshot cache. Thin domain wrapper over
 * {@link SidecarFileCache}; the on-disk file-pair mechanics, TTL handling,
 * atomic writes and SHA-256 keying all live in the shared cache class.
 *
 * <p>The cache key is the canonical URL string (the underlying cache hashes
 * before touching the filesystem). Multiple bookmarks pointing at the same
 * canonical URL share a single cache entry.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScreenshotCacheService {

    private final AppClock appClock;
    private final ConfigService configService;

    private SidecarFileCache cache;

    @PostConstruct
    void init() {
        cache = new SidecarFileCache(
            Path.of(configService.getScreenshotCacheDir()),
            configService.getScreenshotSuccessTtl(),
            configService.getScreenshotNegativeTtl(),
            configService.getScreenshotNegativeTtlMax(),
            appClock, "screenshot", LOG);
    }

    public @NonNull Optional<CachedScreenshot> get(@NonNull String key) {
        return cache.get(key).map(e -> new CachedScreenshot(e.bytes(), e.contentType(), e.negative()));
    }

    public void putSuccess(@NonNull String key, byte @NonNull [] bytes, @NonNull String contentType) {
        cache.putSuccess(key, bytes, contentType);
    }

    public void putNegative(@NonNull String key) {
        cache.putNegative(key);
    }

    public long deleteForKey(@NonNull String key) {
        return cache.delete(key);
    }

    public @NonNull Path getCacheDir() {
        return cache.getCacheDir();
    }

    public static @NonNull String keyFor(@NonNull URL url) {
        return canonicalUrl(url);
    }

    static @NonNull String canonicalUrl(@NonNull URL url) {
        String scheme = url.getProtocol().toLowerCase();
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }
        host = host.toLowerCase();
        int port = url.getPort();
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        String defaultPort = "https".equals(scheme) ? "443" : "80";
        if (port != -1 && !String.valueOf(port).equals(defaultPort)) {
            sb.append(":").append(port);
        }
        String path = url.getPath();
        sb.append(path == null || path.isEmpty() ? "/" : path);
        String query = url.getQuery();
        if (query != null && !query.isEmpty()) {
            sb.append("?").append(query);
        }
        return sb.toString();
    }

    public record CachedScreenshot(byte @NonNull [] bytes, @NonNull String contentType, boolean negative) {}
}
