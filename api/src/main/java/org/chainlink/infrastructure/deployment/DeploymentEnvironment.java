package org.chainlink.infrastructure.deployment;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public enum DeploymentEnvironment {
    LOCAL,
    UNIT_TEST,
    DEV,
    UAT,
    PROD,
    ;

    public boolean isVersionFilterEnabled() {
        return !(this == LOCAL || this == UNIT_TEST);
    }

    public boolean isProd(){
        return this == PROD;
    }

    public boolean isDev(){
        return this == DEV;
    }

    public boolean isTesting(){
        return this == LOCAL || this == UNIT_TEST || this == DEV || this == UAT;
    }
}
