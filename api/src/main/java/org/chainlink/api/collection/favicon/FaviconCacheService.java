package org.chainlink.api.collection.favicon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import ch.dvbern.dvbstarter.clock.AppClock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@Slf4j
@RequiredArgsConstructor
public class FaviconCacheService {

    private final AppClock appClock;
    private final ConfigService configService;

    /**
     * The file extension for the raw favicon bytes on disk. Each cached origin
     * produces two files: {@code <sha256>.bin} (the actual image bytes — could
     * be a .ico, .png, .svg, etc.; the actual MIME type is recorded separately
     * in the sidecar) and {@code <sha256>.meta}. We use {@code .bin} rather
     * than the real image extension because the canonical-origin → file
     * mapping doesn't know the type until after the fetch, and the
     * content-type is what we trust on read anyway.
     */
    private static final String SUFFIX_PAYLOAD = ".bin";

    /**
     * Sidecar file containing content-type, fetched-at epoch, and an optional
     * {@code negative} marker. Always present for a cached origin; the payload
     * file is absent for negative entries.
     *
     * <p>A negative cache entry is a marker that says "we tried to fetch this
     * origin and there was no usable favicon" (host blocked by SSRF, network
     * failure, non-image content-type, oversized, etc.). Without it, every
     * page render of a bookmark whose icon failed would re-trigger an outbound
     * fetch — a "retry storm". The negative marker has a much shorter TTL
     * (default 6h) than a successful entry (30d), so transient failures clear
     * quickly while still suppressing repeated fetches in the meantime.
     */
    private static final String SUFFIX_META = ".meta";

    /**
     * Staging filename for the payload write. We write the bytes to
     * {@code <sha>.bin.tmp} first, then {@code Files.move} to {@code <sha>.bin}
     * with {@code ATOMIC_MOVE}, so a concurrent reader (or a cleanup job per
     * UC-051) never observes a half-written file.
     */
    private static final String SUFFIX_PAYLOAD_TMP = ".bin.tmp";

    private Path cacheDir;

    @PostConstruct
    void init() {
        cacheDir = Path.of(configService.getFaviconCacheDir());
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LOG.warn("Could not create favicon cache directory {}: {}", cacheDir, e.getMessage());
        }
    }

    public @NonNull Optional<CachedFavicon> get(@NonNull String origin) {
        Path bin = filePath(origin, SUFFIX_PAYLOAD);
        Path meta = filePath(origin, SUFFIX_META);
        if (!Files.exists(meta)) {
            return Optional.empty();
        }
        try {
            String[] lines = Files.readString(meta, StandardCharsets.UTF_8).split("\n");
            String contentType = lines.length > 0 ? lines[0] : "";
            long fetchedAt = lines.length > 1 ? Long.parseLong(lines[1]) : 0L;
            boolean negative = lines.length > 2 && "negative".equals(lines[2]);

            Duration age = Duration.between(Instant.ofEpochSecond(fetchedAt), appClock.instant().now());
            Duration ttl = negative ? configService.getFaviconNegativeTtl() : configService.getFaviconSuccessTtl();
            if (age.compareTo(ttl) > 0) {
                return Optional.empty();
            }
            byte[] bytes = negative ? new byte[0] : Files.readAllBytes(bin);
            return Optional.of(new CachedFavicon(bytes, contentType, negative));
        } catch (IOException | NumberFormatException e) {
            LOG.warn("Failed to read favicon cache for origin {}: {}", origin, e.getMessage());
            return Optional.empty();
        }
    }

    public void putSuccess(@NonNull String origin, byte @NonNull [] bytes, @NonNull String contentType) {
        write(origin, bytes, contentType, false);
    }

    public void putNegative(@NonNull String origin) {
        write(origin, new byte[0], "", true);
    }

    private void write(@NonNull String origin, byte[] bytes, @NonNull String contentType, boolean negative) {
        try {
            Path bin = filePath(origin, SUFFIX_PAYLOAD);
            Path meta = filePath(origin, SUFFIX_META);
            Path tmp = filePath(origin, SUFFIX_PAYLOAD_TMP);
            if (!negative) {
                Files.write(tmp, bytes);
                Files.move(tmp, bin, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.deleteIfExists(bin);
            }
            String metaText = contentType + "\n" + appClock.instant().now().getEpochSecond() + (negative ? "\nnegative" : "");
            Files.writeString(meta, metaText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to write favicon cache for origin {}: {}", origin, e.getMessage());
        }
    }

    private @NonNull Path filePath(@NonNull String origin, @NonNull String suffix) {
        return cacheDir.resolve(sha256(origin) + suffix);
    }

    public @NonNull Path getCacheDir() {
        return cacheDir;
    }

    /**
     * Deletes the {@code .bin} and {@code .meta} files for the given origin if present
     * and returns the number of bytes freed. Per UC-051 BR-113 individual I/O failures
     * are logged and treated as zero-bytes-freed so a single bad file does not abort
     * the cleanup run.
     */
    public long deleteForOrigin(@NonNull String origin) {
        long freed = 0L;
        for (String suffix : new String[] { SUFFIX_PAYLOAD, SUFFIX_META }) {
            Path file = filePath(origin, suffix);
            try {
                if (Files.exists(file)) {
                    long size = Files.size(file);
                    Files.delete(file);
                    freed += size;
                }
            } catch (IOException e) {
                LOG.warn("Failed to delete favicon cache file {}: {}", file, e.getMessage());
            }
        }
        return freed;
    }

    static @NonNull String sha256(@NonNull String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record CachedFavicon(byte @NonNull [] bytes, @NonNull String contentType, boolean negative) {}
}
