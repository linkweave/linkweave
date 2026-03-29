package org.chainlink.api.shared.auth;

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
}
