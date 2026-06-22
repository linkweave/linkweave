package org.linkweave.api.shared.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

/**
 * Helpers shared by the favicon and screenshot file-cache cleanup jobs.
 * Extracted because the byte-size parsing and directory-size walking were
 * literally identical in both jobs; the rest of the cleanup loop is similar
 * but diverges enough that a wider abstraction would obscure more than it
 * removes.
 */
public final class FileCacheCleanupUtil {

    private FileCacheCleanupUtil() {
    }

    /**
     * Parses sizes such as {@code "40MB"}, {@code "100K"}, {@code "1G"} or a
     * bare byte count. Spaces and case are tolerated. Throws
     * {@link NumberFormatException} on malformed input — callers should treat
     * the configured value as part of the deployment contract.
     */
    public static long parseSize(@NonNull String raw) {
        String s = raw.trim().toUpperCase();
        long mult = 1L;
        if (s.endsWith("KB") || s.endsWith("K")) {
            mult = 1024L;
            s = s.substring(0, s.length() - (s.endsWith("KB") ? 2 : 1));
        } else if (s.endsWith("MB") || s.endsWith("M")) {
            mult = 1024L * 1024L;
            s = s.substring(0, s.length() - (s.endsWith("MB") ? 2 : 1));
        } else if (s.endsWith("GB") || s.endsWith("G")) {
            mult = 1024L * 1024L * 1024L;
            s = s.substring(0, s.length() - (s.endsWith("GB") ? 2 : 1));
        } else if (s.endsWith("B")) {
            s = s.substring(0, s.length() - 1);
        }
        return Long.parseLong(s.trim()) * mult;
    }

    /**
     * Sums the size of regular files directly inside {@code dir} (non-recursive
     * — matches our flat cache layout). Per-file or directory-level IO failures
     * are logged and treated as zero so a single bad file doesn't abort the
     * whole cleanup pass. The {@code log} argument lets the caller's job name
     * appear in any warning, which matters for ops triage when both caches
     * coexist.
     */
    public static long computeDirectorySize(@NonNull Path dir, @NonNull Logger log) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    log.warn("Failed to stat cache file {}: {}", p, e.getMessage());
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            log.warn("Failed to list cache directory {}: {}", dir, e.getMessage());
            return 0L;
        }
    }
}
