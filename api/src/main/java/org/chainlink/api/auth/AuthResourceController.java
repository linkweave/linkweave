package org.chainlink.api.auth;

import java.util.Set;

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
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Path("/auth")
public class AuthResourceController {

    private final SecurityIdentity identity;
    private final CurrentUserService currentUserService;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response me() {
        if (identity.isAnonymous()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        User user = currentUserService.findCurrentUser().orElse(null);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        return Response.ok(new UserInfo(
            identity.getPrincipal().getName(),
            user.getVorname(),
            user.getNachname(),
            identity.getRoles()
        )).build();
    }

    @POST
    @Path("/logout")
    @Authenticated
    public Response logout() {
        FormAuthenticationMechanism.logout(identity);
        return Response.noContent().build();
    }
}
