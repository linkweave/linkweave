package org.chainlink.api.auth.apikey;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.credential.Credential;

public record ApiKeyCredential(ID<ApiKey> apiKeyId) implements Credential {}
