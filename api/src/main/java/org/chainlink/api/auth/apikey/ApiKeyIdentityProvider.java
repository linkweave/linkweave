package org.chainlink.api.auth.apikey;

import java.util.Set;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@ApplicationScoped
@RequiredArgsConstructor
public class ApiKeyIdentityProvider implements IdentityProvider<ApiKeyRequest> {

    private static final String API_KEY_PREFIX = "cl_";
    private static final int RAW_KEY_LENGTH = 64;

    private final ApiKeyService apiKeyService;

    @Override
    public Class<ApiKeyRequest> getRequestType() {
        return ApiKeyRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
        ApiKeyRequest request,
        AuthenticationRequestContext context
    ) {
        return context.runBlocking(() -> authenticateBlocking(request.getApiKey()));
    }

    @NonNull
    private SecurityIdentity authenticateBlocking(@NonNull String apiKeyHeader) {
        if (!isValidFormat(apiKeyHeader)) {
            throw new AuthenticationFailedException("Invalid or revoked API key");
        }

        String rawKey = apiKeyHeader.substring(API_KEY_PREFIX.length());
        String hash = ApiKeyService.sha256Hex(rawKey);

        var identityData = apiKeyService.buildIdentityFromApiKey(hash);
        if (identityData.isEmpty()) {
            throw new AuthenticationFailedException("Invalid or revoked API key");
        }

        var data = identityData.get();
        QuarkusSecurityIdentity identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(new QuarkusPrincipal(data.email()))
            .addRoles(data.roles())
            .addCredential(new ApiKeyCredential(data.apiKeyId()))
            .addAttribute("auth-method", "api-key")
            .build();

        apiKeyService.updateLastUsedAt(data.apiKeyId());

        return identity;
    }

    private boolean isValidFormat(@NonNull String apiKey) {
        if (!apiKey.startsWith(API_KEY_PREFIX)) {
            return false;
        }
        String raw = apiKey.substring(API_KEY_PREFIX.length());
        if (raw.length() != RAW_KEY_LENGTH) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    public record ApiKeyIdentityData(
        ID<ApiKey> apiKeyId,
        String email,
        Set<String> roles
    ) {}
}
