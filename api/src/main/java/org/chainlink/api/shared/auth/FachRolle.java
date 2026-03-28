package org.chainlink.api.shared.auth;

import java.util.EnumSet;
import java.util.Set;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum FachRolle {

    SUPPORT(
        EnumSet.of(Permission.SUPPORT)
    ),

    USER(
        EnumSet.of(Permission.BOOKMARK_READ, Permission.BOOKMARK_WRITE)
    );

    @Getter(AccessLevel.PACKAGE)
    private final Set<Permission> permissions;

}
