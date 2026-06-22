package org.linkweave.api.shared.net;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A normalized set of host patterns with exact-host and single-level wildcard
 * matching ({@code intranet.local}, {@code *.mycompany.domain} — the latter also
 * matching the bare apex). Parsing is lenient: it splits on commas/newlines,
 * trims, lowercases and de-duplicates, but does not validate.
 *
 * <p>This is the shared matching primitive behind the two host lists that look
 * structurally identical but mean opposite things:
 * <ul>
 *   <li>the per-collection <em>browser fetch allowlist</em>
 *       ({@code BrowserFetchAllowlist}) — hosts the browser may load directly
 *       and which add user-input validation on top of this set;</li>
 *   <li>the global operator <em>backend fetch denylist</em>
 *       ({@code ConfigService.getBackendFetchDenylist}) — hosts the backend is
 *       forbidden to fetch, used as-is (no validation).</li>
 * </ul>
 */
public final class HostPatternSet {

    private static final HostPatternSet EMPTY = new HostPatternSet(List.of());

    private final List<String> patterns;

    private HostPatternSet(@NonNull List<String> patterns) {
        this.patterns = patterns;
    }

    public static @NonNull HostPatternSet parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        List<String> normalized = Arrays.stream(raw.split("[\\r\\n,]"))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
        return normalized.isEmpty() ? EMPTY : new HostPatternSet(normalized);
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
