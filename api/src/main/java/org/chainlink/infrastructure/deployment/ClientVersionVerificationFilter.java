package org.chainlink.infrastructure.deployment;

import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import com.vdurmont.semver4j.Semver;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static org.chainlink.infrastructure.errorhandling.AppValidationMessage.clientVersionVerificationFailed;

@Provider
@PreMatching
@RequestScoped
@Slf4j
@RequiredArgsConstructor
public class ClientVersionVerificationFilter implements ContainerRequestFilter {

    private final ConfigService configService;


    @ConfigProperty(name = "quarkus.http.root-path")
    String httpRootPath;

    @Override
    public void filter(@NonNull ContainerRequestContext containerRequestContext) {
        if (urlIsWhitelisted(containerRequestContext.getUriInfo())) {
            return;
        }
        if (clientIsCompatible(containerRequestContext)) {
            return;
        }
        throw new AppValidationException(clientVersionVerificationFailed());
    }

    private boolean urlIsWhitelisted(@NonNull UriInfo uriInfo) {
        return uriInfo.getPath()
            .startsWith(httpRootPath + "/pub/client-config/");
    }

    private boolean clientIsCompatible(@NonNull ContainerRequestContext req) {
        if (!configService.getEnvironment().isVersionFilterEnabled()
            || !configService.isVersionFilterEnabled()) {
            return true;
        }
        return ClientDeploymentVerifier
            .createDefault(new Semver(configService.getVersion()))
            .clientIsCompatible(req.getHeaderString("X-App-Version"));
    }
}
