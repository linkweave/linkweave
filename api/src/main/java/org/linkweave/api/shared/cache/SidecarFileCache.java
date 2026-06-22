package org.linkweave.api.shared.cache;

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
 * <p>Negative entries also <em>back off</em>: each consecutive failure for the
 * same key doubles the effective negative TTL (from {@code negativeTtl} up to a
 * {@code negativeMaxTtl} ceiling). A site that's down for a minute clears in one
 * TTL, but a permanently-unreachable host stops being re-fetched every short
 * window — sparing both our throughput and the target from repeated hits. The
 * consecutive-failure count rides along in the {@code .meta} file and is read
 * even past expiry (so escalation survives a TTL lapse); a {@code putSuccess}
 * clears it.
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

    // Caps the doubling exponent so neither the failure count nor the shifted
    // TTL can overflow; the ceiling is enforced by negativeMaxTtl regardless.
    private static final int MAX_FAILURE_COUNT = 30;

    private final Path cacheDir;
    private final Duration successTtl;
    private final Duration negativeTtl;
    private final Duration negativeMaxTtl;
    private final AppClock appClock;
    private final String label;
    private final Logger log;

    public SidecarFileCache(
        @NonNull Path cacheDir,
        @NonNull Duration successTtl,
        @NonNull Duration negativeTtl,
        @NonNull Duration negativeMaxTtl,
        @NonNull AppClock appClock,
        @NonNull String label,
        @NonNull Logger log
    ) {
        this.cacheDir = cacheDir;
        this.successTtl = successTtl;
        this.negativeTtl = negativeTtl;
        this.negativeMaxTtl = negativeMaxTtl.compareTo(negativeTtl) < 0 ? negativeTtl : negativeMaxTtl;
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
        Optional<Meta> metaOpt = readMeta(key);
        if (metaOpt.isEmpty()) {
            return Optional.empty();
        }
        Meta meta = metaOpt.get();
        Duration age = Duration.between(Instant.ofEpochSecond(meta.fetchedAt()), appClock.instant().now());
        Duration ttl = meta.negative() ? effectiveNegativeTtl(meta.failureCount()) : successTtl;
        if (age.compareTo(ttl) > 0) {
            return Optional.empty();
        }
        try {
            byte[] bytes = meta.negative() ? new byte[0] : Files.readAllBytes(filePath(key, SUFFIX_PAYLOAD));
            return Optional.of(new Entry(bytes, meta.contentType(), meta.negative()));
        } catch (IOException e) {
            log.warn("Failed to read {} cache payload for key {}: {}", label, key, e.getMessage());
            return Optional.empty();
        }
    }

    public void putSuccess(@NonNull String key, byte @NonNull [] bytes, @NonNull String contentType) {
        write(key, bytes, contentType, false, 0);
    }

    public void putNegative(@NonNull String key) {
        // Escalate from the prior consecutive-failure count, read even if the
        // previous negative entry has already expired. A prior success (or no
        // entry) resets us to the first failure.
        int priorFailures = readMeta(key).filter(Meta::negative).map(Meta::failureCount).orElse(0);
        int failureCount = Math.min(priorFailures + 1, MAX_FAILURE_COUNT);
        write(key, new byte[0], "", true, failureCount);
    }

    /** Doubles the base TTL per consecutive failure, clamped to the configured ceiling. */
    private @NonNull Duration effectiveNegativeTtl(int failureCount) {
        if (failureCount <= 1) {
            return negativeTtl;
        }
        int shift = Math.min(failureCount - 1, MAX_FAILURE_COUNT);
        long scaledSeconds = negativeTtl.toSeconds() << shift;
        if (scaledSeconds <= 0) { // overflow guard
            return negativeMaxTtl;
        }
        Duration scaled = Duration.ofSeconds(scaledSeconds);
        return scaled.compareTo(negativeMaxTtl) > 0 ? negativeMaxTtl : scaled;
    }

    private @NonNull Optional<Meta> readMeta(@NonNull String key) {
        Path meta = filePath(key, SUFFIX_META);
        if (!Files.exists(meta)) {
            return Optional.empty();
        }
        try {
            String[] lines = Files.readString(meta, StandardCharsets.UTF_8).split("\n");
            String contentType = lines.length > 0 ? lines[0] : "";
            long fetchedAt = lines.length > 1 ? Long.parseLong(lines[1]) : 0L;
            boolean negative = lines.length > 2 && "negative".equals(lines[2]);
            // Legacy negative entries (pre-backoff) have no count line → treat as
            // the first failure so they expire at the base TTL as before.
            int failureCount = lines.length > 3 ? Integer.parseInt(lines[3].trim()) : (negative ? 1 : 0);
            return Optional.of(new Meta(contentType, fetchedAt, negative, failureCount));
        } catch (IOException | NumberFormatException e) {
            log.warn("Failed to read {} cache meta for key {}: {}", label, key, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(@NonNull String key, byte[] bytes, @NonNull String contentType, boolean negative, int failureCount) {
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
            String metaText = contentType + "\n" + appClock.instant().now().getEpochSecond()
                + (negative ? "\nnegative\n" + failureCount : "");
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

    /** Parsed {@code .meta} contents, read independently of TTL expiry. */
    private record Meta(@NonNull String contentType, long fetchedAt, boolean negative, int failureCount) {}
}
