package org.chainlink.api.collection.favicon;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class FaviconAllowlist {

    private static final Pattern VALID_PATTERN = Pattern.compile(
        "^(\\*\\.)?[a-z0-9]([a-z0-9\\-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]*[a-z0-9])?)*$"
    );

    private final List<String> patterns;

    private FaviconAllowlist(@NonNull List<String> patterns) {
        this.patterns = patterns;
    }

    public static @NonNull FaviconAllowlist parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return new FaviconAllowlist(List.of());
        }
        List<String> normalized = Arrays.stream(raw.split("[\\r\\n,]"))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
        for (String p : normalized) {
            validatePattern(p);
        }
        return new FaviconAllowlist(normalized);
    }

    private static void validatePattern(@NonNull String pattern) {
        if (pattern.equals("*")
            || pattern.contains("/")
            || pattern.contains(":")
            || pattern.contains("://")
            || !VALID_PATTERN.matcher(pattern).matches()
            || isBareIpv4(pattern)) {
            throw new AppValidationException(AppValidationMessage.faviconAllowlistInvalidPattern(pattern));
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
        String h = host.toLowerCase();
        return patterns.stream().anyMatch(p -> matchesPattern(p, h));
    }

    private static boolean matchesPattern(@NonNull String pattern, @NonNull String host) {
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1);
            return host.endsWith(suffix) || host.equals(pattern.substring(2));
        }
        return host.equals(pattern);
    }

    public @NonNull List<String> patterns() {
        return patterns;
    }
}
