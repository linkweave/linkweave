package org.chainlink.api.collection;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.api.shared.user.User;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections")
@Slf4j
public class CollectionResource {

    private final CollectionService collectionService;
    private final CollectionSettingsService collectionSettingsService;
    private final AuthorizationService authorizationService;
    private final CurrentUserService currentUserService;
    private final CollectionInfoMapper collectionInfoMapper;

    @GET
    @NonNull
    @Authenticated
    public CollectionSummaryListJson listCollections() {
        var currentUser = currentUserService.currentUser();
        return new CollectionSummaryListJson(collectionService.findCollectionsForUser(currentUser));
    }

    @GET
    @Path("{id}")
    @NonNull
    @Authenticated
    public CollectionInfoJson getCollectionInfoById(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        return collectionInfoMapper.toCollectionInfoJson(id);
    }

    @POST
    @NonNull
    @Authenticated
    public CollectionSummaryJson createCollection(@Valid CollectionCreateJson json) {
        var currentUser = currentUserService.currentUser();
        return collectionService.createCollection(json.getName(), currentUser);
    }

    @PUT
    @Path("{id}/default")
    @Authenticated
    public void setDefaultCollection(
        @PathParam("id") ID<Collection> id
    ) {
        authorizationService.requireCollectionAccess(id);
        var currentUser = currentUserService.currentUser();
        collectionService.setDefaultCollection(id, currentUser);
    }


    @PUT
    @Path("{id}")
    @NonNull
    @Authenticated
    public CollectionSummaryJson updateCollection(
        @PathParam("id") ID<Collection> id,
        @Valid CollectionUpdateJson json
    ) {
        authorizationService.requireOwnerAccess(id);
        var currentUser = currentUserService.currentUser();
        return collectionService.updateCollection(id, json.getName(), json.getFaviconAllowlist(), currentUser);
    }

    @DELETE
    @Path("{id}")
    @Authenticated
    public void deleteCollection(@PathParam("id") ID<Collection> id) {
        authorizationService.requireOwnerAccess(id);
        var currentUser = currentUserService.currentUser();
        collectionService.deleteCollection(id, currentUser);
    }

    @GET
    @Path("{id}/members")
    @NonNull
    @Authenticated
    public CollectionMemberListJson listMembers(@PathParam("id") ID<Collection> id) {
        authorizationService.requireOwnerAccess(id);
        return new CollectionMemberListJson(collectionService.listMembers(id));
    }

    @POST
    @Path("{id}/members")
    @NonNull
    @Authenticated
    public CollectionMemberJson shareWithUser(
        @PathParam("id") ID<Collection> id,
        @Valid CollectionShareJson collectionShareJson
    ) {
        authorizationService.requireOwnerAccess(id);
        var currentUser = currentUserService.currentUser();
        return collectionService.shareWithUser(id, collectionShareJson.getEmail(), currentUser);
    }

    @GET
    @Path("{id}/settings")
    @NonNull
    @Authenticated
    public CollectionSettingsJson getSettings(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        return collectionSettingsService.getSettings(id);
    }

    @PUT
    @Path("{id}/settings")
    @NonNull
    @Authenticated
    public CollectionSettingsJson updateSettings(
        @PathParam("id") ID<Collection> id,
        @Valid CollectionSettingsJson json
    ) {
        authorizationService.requireCollectionAccess(id);
        return collectionSettingsService.updateSettings(id, json);
    }

    @DELETE
    @Path("{id}/settings/sort")
    @Authenticated
    public void resetSortPreference(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        collectionSettingsService.resetSortPreference(id);
    }

    @DELETE
    @Path("{id}/members/{userId}")
    @Authenticated
    public void revokeAccess(
        @PathParam("id") ID<Collection> id,
        @PathParam("userId") ID<User> userId
    ) {
        authorizationService.requireOwnerAccess(id);
        collectionService.revokeAccess(id, userId);
    }

}
