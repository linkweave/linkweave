package org.chainlink.api.auth;

import java.net.URI;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.oidc.OidcSession;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionService;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.errorhandling.AppAuthException;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Path("/auth")
@Slf4j
public class AuthResource {

    private final SecurityIdentity identity;
    private final CurrentUserService currentUserService;
    private final CollectionService collectionService;
    private final Instance<OidcSession> oidcSessionInstance;
    private final EnsureUserService ensureUserService;

    private final  UriInfo uriInfo;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public UserInfoJson me() {
        if (identity.isAnonymous()) {
            throw new AppAuthException();
        }

        User user = currentUserService.findCurrentUser().orElse(null);
        if (user == null) {
            throw new AppAuthException();
        }

        Collection collection = collectionService.getDefaultCollectionOrAutoprovision(user);

        ID<Collection> defaultCollectionId = collection.getId();

        return new UserInfoJson(
            identity.getPrincipal().getName(),
            user.getVorname(),
            user.getNachname(),
            identity.getRoles(),
            defaultCollectionId
        );
    }

    @GET
    @Path("/oidc-login")
    @Authenticated
    @Transactional(TxType.NOT_SUPPORTED)
    public Response oidcLogin() {
        User _ = ensureUserService.ensureUserExists();

        URI baseUri = uriInfo.getBaseUriBuilder()
            .replacePath(null)
            .build();
        return Response.seeOther(baseUri).build();
    }


    @POST
    @Path("/logout")
    @Authenticated
    public Response logout() {
        if (oidcSessionInstance.isResolvable()) {
            oidcSessionInstance.get().logout().await().indefinitely();
        } else {
            FormAuthenticationMechanism.logout(identity);
        }
        return Response.noContent().build();
    }
}
