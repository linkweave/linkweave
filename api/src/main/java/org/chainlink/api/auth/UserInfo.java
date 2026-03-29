package org.chainlink.api.auth;

import java.util.Set;

public record UserInfo(
    String email,
    String firstName,
    String lastName,
    Set<String> roles
) {
}
