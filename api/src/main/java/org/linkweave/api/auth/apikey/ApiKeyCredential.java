package org.linkweave.api.auth.apikey;

import org.linkweave.api.types.id.ID;
import io.quarkus.security.credential.Credential;

public record ApiKeyCredential(ID<ApiKey> apiKeyId) implements Credential {}
