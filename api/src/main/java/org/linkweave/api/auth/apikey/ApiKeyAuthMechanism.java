package org.linkweave.api.auth.apikey;

import java.util.Set;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ApiKeyAuthMechanism implements HttpAuthenticationMechanism {

    private static final String HEADER_NAME = "X-API-Key";

    @Override
    public Uni<SecurityIdentity> authenticate(
        RoutingContext context,
        IdentityProviderManager identityProviderManager
    ) {
        String apiKeyHeader = context.request().getHeader(HEADER_NAME);
        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            return Uni.createFrom().nullItem();
        }

        return identityProviderManager.authenticate(new ApiKeyRequest(apiKeyHeader));
    }

    @Override
    public Uni<ChallengeData> getChallenge(
        RoutingContext context
    ) {
        String apiKeyHeader = context.request().getHeader(HEADER_NAME);
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return Uni.createFrom().item(new ChallengeData(401));
        }
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(ApiKeyRequest.class);
    }

    @Override
    public int getPriority() {
        return 2100;
    }
}
