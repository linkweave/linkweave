package org.linkweave.api.bookmark.property;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.property.json.PropertyDefinitionJson;
import org.linkweave.api.bookmark.property.json.PropertyDefinitionListJson;
import org.linkweave.api.bookmark.property.json.PropertyDefinitionSaveJson;
import org.linkweave.api.bookmark.property.json.PropertyDefinitionUsageJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/property-definitions")
public class PropertyDefinitionResource {

    private final PropertyDefinitionService propertyDefinitionService;
    private final AuthorizationService authorizationService;
    private final ConfigService configService;

    private void requireFeatureEnabled() {
        if (!configService.isBookmarkPropertiesEnabled()) {
            throw new NotFoundException();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public PropertyDefinitionListJson list(@QueryParam("collectionId") @NotNull @NonNull ID<Collection> collectionId) {
        requireFeatureEnabled();
        authorizationService.requireCollectionAccess(collectionId);
        return new PropertyDefinitionListJson(
            propertyDefinitionService.findByCollection(collectionId).stream()
                .map(PropertyDefinitionMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public PropertyDefinitionJson create(@NotNull @Valid @NonNull PropertyDefinitionSaveJson json) {
        requireFeatureEnabled();
        authorizationService.requireCollectionAccess(json.getCollectionId());
        PropertyDefinition def = propertyDefinitionService.create(json);
        return PropertyDefinitionMapper.toJson(def);
    }

    @PUT
    @Path("/{definitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public PropertyDefinitionJson update(
        @PathParam("definitionId") @NotNull @NonNull ID<PropertyDefinition> definitionId,
        @NotNull @Valid @NonNull PropertyDefinitionSaveJson json
    ) {
        requireFeatureEnabled();
        PropertyDefinition def = propertyDefinitionService.getById(definitionId);
        authorizationService.requireAccessTo(def);
        PropertyDefinition updated = propertyDefinitionService.update(def, json);
        return PropertyDefinitionMapper.toJson(updated);
    }

    @DELETE
    @Path("/{definitionId}")
    @Authenticated
    public void delete(
        @PathParam("definitionId") @NotNull @NonNull ID<PropertyDefinition> definitionId
    ) {
        requireFeatureEnabled();
        PropertyDefinition def = propertyDefinitionService.getById(definitionId);
        authorizationService.requireAccessTo(def);
        propertyDefinitionService.remove(definitionId);
    }

    @GET
    @Path("/{definitionId}/usage")
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public PropertyDefinitionUsageJson usage(
        @PathParam("definitionId") @NotNull @NonNull ID<PropertyDefinition> definitionId
    ) {
        requireFeatureEnabled();
        PropertyDefinition def = propertyDefinitionService.getById(definitionId);
        authorizationService.requireAccessTo(def);
        return new PropertyDefinitionUsageJson(propertyDefinitionService.countUsage(definitionId));
    }
}
