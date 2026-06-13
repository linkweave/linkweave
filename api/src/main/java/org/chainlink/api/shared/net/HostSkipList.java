package org.chainlink.api.shared.net;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Operator-facing, global list of hostnames the backend must never fetch
 * (favicons or screenshots) over the network. Distinct from the user-facing
 * per-collection {@code FaviconAllowlist}
 *
 * <p>Patterns are exact hostnames ({@code intranet.local}) or single-level
 * wildcards ({@code *.mycompany.domain}, which also matches the bare apex).
 * Parsing is lenient — this is operator config, not user input, so malformed
 * entries are simply normalized rather than rejected.
 */
public final class HostSkipList {

    private static final HostSkipList EMPTY = new HostSkipList(List.of());

    private final List<String> patterns;

    private HostSkipList(@NonNull List<String> patterns) {
        this.patterns = patterns;
    }

    public static @NonNull HostSkipList parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        List<String> normalized = Arrays.stream(raw.split("[\\r\\n,]"))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
        return normalized.isEmpty() ? EMPTY : new HostSkipList(normalized);
    }

    public boolean matches(@Nullable String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase();
        return patterns.stream().anyMatch(p -> matchesPattern(p, h));
    }

    private static boolean matchesPattern(@NonNull String pattern, @NonNull String host) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1); // ".mycompany.domain"
            return host.endsWith(suffix) || host.equals(pattern.substring(2));
        }
        return host.equals(pattern);
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public @NonNull List<String> patterns() {
        return patterns;
    }
}
