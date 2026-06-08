package org.chainlink.api.auth.apikey;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public enum ApiKeyExpiration {

    DAYS_30("30d", Duration.ofDays(30)),
    DAYS_90("90d", Duration.ofDays(90)),
    YEARS_1("1y", Duration.ofDays(365)),
    NEVER("never", null);

    private final String value;
    private final Duration duration;

    ApiKeyExpiration(@NonNull String value, @Nullable Duration duration) {
        this.value = value;
        this.duration = duration;
    }

    public @NonNull String getValue() {
        return value;
    }

    public @Nullable OffsetDateTime computeExpiresAt(@NonNull OffsetDateTime now) {
        if (duration == null) {
            return null;
        }
        return now.plus(duration);
    }

    public static @NonNull Optional<ApiKeyExpiration> fromValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(NEVER);
        }
        String normalized = value.toLowerCase().trim();
        for (ApiKeyExpiration exp : values()) {
            if (exp.value.equals(normalized)) {
                return Optional.of(exp);
            }
        }
        return Optional.empty();
    }
}
