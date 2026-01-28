package org.chainlink.infrastructure.errorhandling;

import lombok.Getter;

@Getter
public enum FailureType {
    VALIDATION(true),
    FAILURE(false);

    private final boolean userInfo;

    FailureType(boolean userInfo) {
        this.userInfo = userInfo;
    }

    public boolean isFailure() {
        return !userInfo;
    }
}
