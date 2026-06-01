package org.chainlink.api.shared.cache;

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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

/**
 * On-disk binary cache backed by sidecar {@code .bin}/{@code .meta} file pairs,
 * keyed by SHA-256 of an arbitrary caller-supplied logical key.
 *
 * <p>Each cached entry produces:
 * <ul>
 *   <li>{@code <sha>.bin} — the raw bytes (omitted for negative entries)</li>
 *   <li>{@code <sha>.meta} — content-type, fetched-at epoch seconds, and an
 *       optional {@code "negative"} marker line</li>
 * </ul>
 *
 * <p>Writes are atomic via temp file + {@link StandardCopyOption#ATOMIC_MOVE},
 * so concurrent readers (or the cleanup job sweeping the directory) never see
 * a half-written file. A negative entry is a marker that says "we tried and
 * there is no usable result"; without it, every render of a bookmark whose
 * fetch failed would retry — a retry storm. Negative entries have a much
 * shorter TTL than successful ones so transient failures clear quickly.
 *
 * <p>Not a CDI bean. Construct one per logical cache (favicon, screenshot,
 * future …) inside the owning service's {@code @PostConstruct}, supplying
 * cache directory, TTLs, clock, a short label for log triage, and the
 * owner's logger.
 */
public final class SidecarFileCache {

    private static final String SUFFIX_PAYLOAD = ".bin";
    private static final String SUFFIX_META = ".meta";
    private static final String SUFFIX_PAYLOAD_TMP = ".bin.tmp";

    private final Path cacheDir;
    private final Duration successTtl;
    private final Duration negativeTtl;
    private final AppClock appClock;
    private final String label;
    private final Logger log;

    public SidecarFileCache(
        @NonNull Path cacheDir,
        @NonNull Duration successTtl,
        @NonNull Duration negativeTtl,
        @NonNull AppClock appClock,
        @NonNull String label,
        @NonNull Logger log
    ) {
        this.cacheDir = cacheDir;
        this.successTtl = successTtl;
        this.negativeTtl = negativeTtl;
        this.appClock = appClock;
        this.label = label;
        this.log = log;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            log.warn("Could not create {} cache directory {}: {}", label, cacheDir, e.getMessage());
        }
    }

    public @NonNull Optional<Entry> get(@NonNull String key) {
        Path meta = filePath(key, SUFFIX_META);
        if (!Files.exists(meta)) {
            return Optional.empty();
        }
        try {
            String[] lines = Files.readString(meta, StandardCharsets.UTF_8).split("\n");
            String contentType = lines.length > 0 ? lines[0] : "";
            long fetchedAt = lines.length > 1 ? Long.parseLong(lines[1]) : 0L;
            boolean negative = lines.length > 2 && "negative".equals(lines[2]);

            Duration age = Duration.between(Instant.ofEpochSecond(fetchedAt), appClock.instant().now());
            Duration ttl = negative ? negativeTtl : successTtl;
            if (age.compareTo(ttl) > 0) {
                return Optional.empty();
            }
            byte[] bytes = negative ? new byte[0] : Files.readAllBytes(filePath(key, SUFFIX_PAYLOAD));
            return Optional.of(new Entry(bytes, contentType, negative));
        } catch (IOException | NumberFormatException e) {
            log.warn("Failed to read {} cache for key {}: {}", label, key, e.getMessage());
            return Optional.empty();
        }
    }

    public void putSuccess(@NonNull String key, byte @NonNull [] bytes, @NonNull String contentType) {
        write(key, bytes, contentType, false);
    }

    public void putNegative(@NonNull String key) {
        write(key, new byte[0], "", true);
    }

    private void write(@NonNull String key, byte[] bytes, @NonNull String contentType, boolean negative) {
        try {
            Path bin = filePath(key, SUFFIX_PAYLOAD);
            Path meta = filePath(key, SUFFIX_META);
            Path tmp = filePath(key, SUFFIX_PAYLOAD_TMP);
            if (!negative) {
                Files.write(tmp, bytes);
                Files.move(tmp, bin, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.deleteIfExists(bin);
            }
            String metaText = contentType + "\n" + appClock.instant().now().getEpochSecond() + (negative ? "\nnegative" : "");
            Files.writeString(meta, metaText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to write {} cache for key {}: {}", label, key, e.getMessage());
        }
    }

    /**
     * Deletes both files for {@code key} if present and returns the number of
     * bytes freed. Individual IO failures are logged and treated as zero so a
     * single bad file doesn't abort a sweep.
     */
    public long delete(@NonNull String key) {
        long freed = 0L;
        for (String suffix : new String[] { SUFFIX_PAYLOAD, SUFFIX_META }) {
            Path file = filePath(key, suffix);
            try {
                if (Files.exists(file)) {
                    long size = Files.size(file);
                    Files.delete(file);
                    freed += size;
                }
            } catch (IOException e) {
                log.warn("Failed to delete {} cache file {}: {}", label, file, e.getMessage());
            }
        }
        return freed;
    }

    public @NonNull Path getCacheDir() {
        return cacheDir;
    }

    private @NonNull Path filePath(@NonNull String key, @NonNull String suffix) {
        return cacheDir.resolve(sha256(key) + suffix);
    }

    public static @NonNull String sha256(@NonNull String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Entry(byte @NonNull [] bytes, @NonNull String contentType, boolean negative) {}
}
