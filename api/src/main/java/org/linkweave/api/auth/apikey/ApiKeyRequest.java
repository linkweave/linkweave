package org.linkweave.api.auth.apikey;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;
import org.jspecify.annotations.NonNull;

public class ApiKeyRequest extends BaseAuthenticationRequest {

    private final String apiKey;

    public ApiKeyRequest(@NonNull String apiKey) {
        this.apiKey = apiKey;
    }

    @NonNull
    public String getApiKey() {
        return apiKey;
    }
}
