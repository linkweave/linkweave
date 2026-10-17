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
import org.linkweave.infrastructure.db.RetryOnSqliteBusy;
import org.linkweave.infrastructure.ratelimit.RateLimitConst;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RateLimit(value = RateLimitConst.STANDARD_PER_MINUTE, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Path("/auth")
@Authenticated
@Slf4j
// BR-109-3: the identity read runs in front of every request, so contention here fails
// everything at once rather than one save. Safe to re-execute: /me, /register, /settings and
// /oidc-login are pure database work. /logout and DELETE /me clear the session *before* the
// commit, so a commit-time rollback does re-run that — which is harmless on both paths:
// FormAuthenticationMechanism.logout only rewrites the response cookie, and OidcSession.logout
// (quarkus-oidc 3.36.2, the version on the classpath; unchanged in 3.40.1, the LTS we will move
// to -- OidcSessionImpl -> OidcUtils.removeSessionCookie) sets Clear-Site-Data,
// expires the cookie in the response, and hands any stored token state to the configured
// TokenStateManager — which for the default, cookie-based manager has nothing to delete.
//
// Anything that makes that call do remote work breaks this, and two of the ways are silent --
// they would not bring the author anywhere near this comment. RP-initiated, back-channel or
// front-channel logout is pure configuration (quarkus.oidc.logout.*), and a TokenStateManager of
// our own would be wired by CDI from anywhere. AuthResourceRetrySafetyITest asserts neither has
// happened, so those fail the build rather than the logout. Token revocation is the third way and
// is not guarded, because in quarkus-oidc 3.36.2 it is not configuration: it means calling
// OidcProviderClient#revokeAccessToken/revokeRefreshToken, which has to be added here.
//
// Note for whoever adds the next method: this annotation is class-level, so a new endpoint
// inherits the retry boundary without being asked whether it can be re-executed. The ArchUnit rule
// only checks that a resource takes a position, not that each method deserves the one it
// inherited. Anything added here that is not pure database work -- an outbound call, a mail, a
// file write, any non-idempotent side effect -- needs @RetryOnSqliteBusy(attempts = 1) on the
// method, or a resource of its own.
@RetryOnSqliteBusy
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
    // Fewer attempts than the class, because this method has its own much smaller bucket and
    // every attempt spends a permit from it (see SqliteBusyRetryInterceptor on the ordering that
    // makes that true). At the class default of 5 one contended registration would spend all five
    // permits and shut registration for the rest of the minute — turning contention into an
    // outage for everyone instead of a retry for one caller. Two attempts still absorb the single
    // collision that is by far the likeliest, and leave the bucket usable.
    @RetryOnSqliteBusy(attempts = 2)
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
