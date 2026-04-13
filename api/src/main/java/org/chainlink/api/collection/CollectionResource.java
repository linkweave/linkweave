package org.chainlink.api.collection;

import java.util.List;

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
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections")
@Slf4j
public class CollectionResource {

    private final CollectionService collectionService;
    private final AuthorizationService authorizationService;
    private final CurrentUserService currentUserService;

    @GET
    @NonNull
    public List<CollectionSummaryJson> listCollections() {
        var currentUser = currentUserService.currentUser();
        return collectionService.findCollectionsForUser(currentUser);
    }

    @GET
    @Path("{id}")
    @NonNull
    public CollectionInfoJson getCollectionInfoById(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        return collectionService.getCollectionInfoById(id);
    }

    @POST
    @NonNull
    public CollectionSummaryJson createCollection(@Valid CollectionCreateJson json) {
        var currentUser = currentUserService.currentUser();
        return collectionService.createCollection(json.getName(), currentUser);
    }

    @PUT
    @Path("{id}/default")
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
    public CollectionSummaryJson updateCollection(
        @PathParam("id") ID<Collection> id,
        @Valid CollectionUpdateJson json
    ) {
        authorizationService.requireCollectionAccess(id);
        authorizationService.requireOwnerAccess(id);
        return collectionService.updateCollection(id, json.getName());
    }

    @DELETE
    @Path("{id}")
    public void deleteCollection(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        authorizationService.requireOwnerAccess(id);
        var currentUser = currentUserService.currentUser();
        collectionService.deleteCollection(id, currentUser);
    }

}
