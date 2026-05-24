package org.chainlink.api.bookmark;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.SavedSearchJson;
import org.chainlink.api.bookmark.json.SavedSearchListJson;
import org.chainlink.api.bookmark.json.SavedSearchSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/saved-searches")
public class SavedSearchResource {

    private final SavedSearchService savedSearchService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public SavedSearchListJson list(@QueryParam("collectionId") @NotNull @NonNull ID<Collection> collectionId) {
        authorizationService.requireCollectionAccess(collectionId);
        return new SavedSearchListJson(
            savedSearchService.findByCollection(collectionId).stream()
                .map(SavedSearchMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public SavedSearchJson create(@NotNull @Valid @NonNull SavedSearchSaveJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        SavedSearch saved = savedSearchService.createSavedSearch(json);
        return SavedSearchMapper.toJson(saved);
    }

    @PUT
    @Path("/{savedSearchId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public SavedSearchJson update(
        @PathParam("savedSearchId") @NotNull @NonNull ID<SavedSearch> savedSearchId,
        @NotNull @Valid @NonNull SavedSearchSaveJson json
    ) {
        SavedSearch saved = savedSearchService.getSavedSearch(savedSearchId);
        authorizationService.requireAccessTo(saved);
        if (!json.getCollectionId().equals(saved.getCollection().getId())) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.savedSearch.collectionMismatch")
            );
        }
        SavedSearch updated = savedSearchService.updateSavedSearch(saved, json);
        return SavedSearchMapper.toJson(updated);
    }

    @DELETE
    @Path("/{savedSearchId}")
    @Authenticated
    public void delete(@PathParam("savedSearchId") @NotNull @NonNull ID<SavedSearch> savedSearchId) {
        SavedSearch saved = savedSearchService.getSavedSearch(savedSearchId);
        authorizationService.requireAccessTo(saved);
        savedSearchService.removeSavedSearch(savedSearchId);
    }
}
