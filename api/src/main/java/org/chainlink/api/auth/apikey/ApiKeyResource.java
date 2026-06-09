package org.chainlink.api.auth.apikey;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.auth.apikey.json.ApiKeyCreateJson;
import org.chainlink.api.auth.apikey.json.ApiKeyListJson;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/auth/api-keys")
public class ApiKeyResource {

    private final ApiKeyService apiKeyService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    public ApiKeyListJson list() {
        return new ApiKeyListJson(
            apiKeyService.listApiKeysForCurrentUser().stream()
                .map(ApiKeyMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @RateLimit(value = 5, window = 1, windowUnit = ChronoUnit.MINUTES)
    public Response create(@NotNull @Valid @NonNull ApiKeyCreateJson json) {
        var result = apiKeyService.createApiKey(json);
        return Response.status(Response.Status.CREATED)
            .entity(ApiKeyMapper.toCreatedJson(result.apiKey(), result.rawKey()))
            .build();
    }

    @DELETE
    @Path("/{apiKeyId}")
    @Authenticated
    public Response revoke(@PathParam("apiKeyId") @NotNull @NonNull ID<ApiKey> apiKeyId) {
        authorizationService.requireApiKeyOwnership(apiKeyId);
        apiKeyService.revokeApiKey(apiKeyId);
        return Response.noContent().build();
    }
}
