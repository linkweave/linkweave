package org.chainlink.api.auth;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccessRepo;
import org.chainlink.api.collection.CollectionService;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.errorhandling.AppAuthException;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Path("/auth")
public class AuthResource {

    private final SecurityIdentity identity;
    private final CurrentUserService currentUserService;
    private final CollectionService collectionService;
    private final CollectionAccessRepo collectionAccessRepo;

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

        collectionService.autoProvisionForUser(user);

        ID<Collection> defaultCollectionId = collectionAccessRepo
            .getDefaultByUser(user.getId())
            .collection.getId();

        return new UserInfoJson(
            identity.getPrincipal().getName(),
            user.getVorname(),
            user.getNachname(),
            identity.getRoles(),
            defaultCollectionId
        );
    }

    @POST
    @Path("/logout")
    @Authenticated
    public Response logout() {
        FormAuthenticationMechanism.logout(identity);
        return Response.noContent().build();
    }
}
