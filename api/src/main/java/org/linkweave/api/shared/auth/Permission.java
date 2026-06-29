package org.linkweave.api.shared.auth;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import lombok.Getter;

@Getter
public enum Permission {

    //SYSTEM_ADMIN
    SUPPORT(BerechtigungName.SUPPORT),
    SYSTEM_ADMIN(BerechtigungName.SYSTEM_ADMIN),

    // Bookmark editing

    BOOKMARK_READ(BerechtigungName.BOOKMARK_READ),
    BOOKMARK_WRITE(BerechtigungName.BOOKMARK_WRITE);

    private final String berechtigungName;

    Permission(String berechtigungName) {
        this.berechtigungName = berechtigungName;
    }

    /**
     * Resolves a {@link Permission} from its enum name, returning empty for any
     * value that isn't a known permission. Security-identity role strings can
     * include non-permission entries (e.g. FachRolle names injected via
     * {@code @TestSecurity}), so callers must tolerate unknown values.
     */
    public static Optional<Permission> fromName(@Nullable String name) {
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Permission.valueOf(name));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
