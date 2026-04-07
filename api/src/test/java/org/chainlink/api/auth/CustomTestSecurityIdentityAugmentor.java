package org.chainlink.api.auth;

import java.util.Map;
import java.util.UUID;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
@IfBuildProfile("test")  // only active when quarkus.profile=test
public class CustomTestSecurityIdentityAugmentor implements SecurityIdentityAugmentor {
    private String uuid;

    @Override
    public Uni<SecurityIdentity> augment(
        SecurityIdentity securityIdentity,
        AuthenticationRequestContext authenticationRequestContext
    ) {
        if (LaunchMode.current() != LaunchMode.TEST) {
            throw new IllegalStateException("CustomTestSecurityIdentityAugmentor must only be used in test mode");
        }
        if (uuid == null) {
            return Uni.createFrom().item(securityIdentity);
        }
        final SecurityIdentity augmentedIdentity;
        augmentedIdentity = QuarkusSecurityIdentity.builder(securityIdentity)
            .setPrincipal(new QuarkusPrincipal("email_" + uuid + "@example.com"))
            .addAttributes(Map.of(
                "sub", uuid,
                "family_name", "fn_" + uuid,
                "given_name", "gn" + uuid
            ))
            .build();
        return Uni.createFrom().item(augmentedIdentity);
    }

    public void setUuid(@Nullable UUID uuid) {
        if (uuid == null) {
            this.uuid = null;
        } else {
            this.uuid = uuid.toString();
        }
    }
}
