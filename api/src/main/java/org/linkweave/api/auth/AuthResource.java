package org.linkweave.api.auth;

import java.net.URI;
import java.time.temporal.ChronoUnit;

import org.linkweave.api.types.id.ID;
import io.quarkus.oidc.OidcSession;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.shared.user.User;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Path("/auth")
@Authenticated
@Slf4j
public class AuthResource {

    private final SecurityIdentity identity;
    private final CurrentUserService currentUserService;
    private final Instance<OidcSession> oidcSessionInstance;
    private final EnsureUserService ensureUserService;
    private final RegistrationService registrationService;
    private final UserService userService;
    private final UserSettingsService userSettingsService;

    private final  UriInfo uriInfo;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    public UserInfoJson me() {
        return userService.buildCurrentUserInfo(identity.getPrincipal().getName(), identity.getRoles());
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
    @Path("/register")
    @Transactional(TxType.NOT_SUPPORTED)
    @PermitAll
    @RateLimit(value = 5, window = 1, windowUnit = ChronoUnit.MINUTES)
    public Response register(@Valid RegistrationRequestJson request) {
        try {
            registrationService.register(
                request.email(),
                request.password(),
                request.vorname(),
                request.nachname()
            );
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(e.getMessage())
                .build();
        }
    }


    @DELETE
    @Path("/me")
    @Authenticated
    public Response deleteCurrentUser() {
        ID<User> userId = currentUserService.currentUserID();
        userService.hardDeleteUser(userId);

        // Drop the session cookie so the now-orphaned credential isn't reused.
        if (oidcSessionInstance.isResolvable()
                && identity.getPrincipal() instanceof JsonWebToken) {
            oidcSessionInstance.get().logout().await().indefinitely();
        } else {
            FormAuthenticationMechanism.logout(identity);
        }

        return Response.noContent().build();
    }

    @PUT
    @Path("/settings")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    public UserSettingsJson updateSettings(@Valid UserSettingsUpdateJson json) {
        return userSettingsService.updateSettings(json);
    }

    @POST
    @Path("/logout")
    @Authenticated
    public Response logout() {
        if (identity == null || identity.isAnonymous()) {
            return Response.noContent().build();
        }

        if (oidcSessionInstance.isResolvable()
                && identity.getPrincipal() instanceof JsonWebToken) {
            oidcSessionInstance.get().logout().await().indefinitely();
        } else {
            FormAuthenticationMechanism.logout(identity);
        }

        return Response.noContent().build();
    }
}
