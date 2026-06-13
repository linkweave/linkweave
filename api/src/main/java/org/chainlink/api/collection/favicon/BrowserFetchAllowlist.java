package org.chainlink.api.collection.favicon;

import java.util.List;
import java.util.regex.Pattern;

import org.chainlink.api.shared.net.HostPatternSet;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A collection's <em>browser fetch allowlist</em>: hosts whose favicon the
 * user's browser loads directly (e.g. {@code intranet.local},
 * {@code *.mycompany.domain}) because the backend can't reach them. The same
 * list doubles as a backend suppression rule — the server skips favicon and
 * screenshot fetches for these hosts entirely.
 *
 * <p>This is user input, so it validates strictly on top of the shared
 * {@link HostPatternSet} matching primitive; malformed patterns are rejected
 * with {@link AppValidationException}.
 */
public final class BrowserFetchAllowlist {

    private static final Pattern VALID_PATTERN = Pattern.compile(
        "^(\\*\\.)?[a-z0-9]([a-z0-9\\-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]*[a-z0-9])?)*$"
    );

    private final HostPatternSet patterns;

    private BrowserFetchAllowlist(@NonNull HostPatternSet patterns) {
        this.patterns = patterns;
    }

    public static @NonNull BrowserFetchAllowlist parse(@Nullable String raw) {
        HostPatternSet set = HostPatternSet.parse(raw);
        set.patterns().forEach(BrowserFetchAllowlist::validatePattern);
        return new BrowserFetchAllowlist(set);
    }

    private static void validatePattern(@NonNull String pattern) {
        if (pattern.equals("*")
            || pattern.contains("/")
            || pattern.contains(":")
            || pattern.contains("://")
            || !VALID_PATTERN.matcher(pattern).matches()
            || isBareIpv4(pattern)) {
            throw new AppValidationException(AppValidationMessage.browserFetchAllowlistInvalidPattern(pattern));
        }
    }

    private static boolean isBareIpv4(@NonNull String pattern) {
        String[] labels = pattern.split("\\.");
        if (labels.length != 4) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || !label.chars().allMatch(Character::isDigit)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(@NonNull String host) {
        return patterns.matches(host);
    }

    public @NonNull List<String> patterns() {
        return patterns.patterns();
    }
}
