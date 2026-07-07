package org.linkweave.api.admin;

import static org.linkweave.api.shared.auth.BerechtigungName.SUPPORT;

import java.time.temporal.ChronoUnit;

import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.db.RetryOnSqliteBusy;
import org.linkweave.infrastructure.stereotypes.JaxResource;

import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@RetryOnSqliteBusy
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/admin")
@Slf4j
public class AdminResource {

    private final AdminService adminService;

    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(SUPPORT)
    @NonNull
    public AdminUserListJson listUsers() {
        return adminService.listAllUsers();
    }

    @POST
    @Path("/users/{userId}/reset-password")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(SUPPORT)
    @NonNull
    public PasswordResetResultJson resetUserPassword(@PathParam("userId") ID<User> userId) {
        return adminService.resetUserPassword(userId);
    }
}
