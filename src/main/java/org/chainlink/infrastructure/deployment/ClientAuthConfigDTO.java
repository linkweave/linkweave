package org.chainlink.infrastructure.deployment;

import java.net.URL;

public interface ClientAuthConfigDTO {

    String clientId();

    String realm();

    /**
     * URL to the Keycloak server, for example: https://keycloak-server/auth
     */
    URL authUrl();
}
