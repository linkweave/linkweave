package org.chainlink.api.collection;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections")
public class CollectionResource {

    private final CollectionService collectionService;
    private final AuthorizationService authorizationService;
    private final CurrentUserService currentUserService;

    @GET
    public List<CollectionSummaryJson> listCollections() {
        var currentUser = currentUserService.currentUser();
        return collectionService.findCollectionsForUser(currentUser);
    }

    @GET
    @Path("{id}")
    public CollectionInfoJson getCollectionInfoById(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        return collectionService.getCollectionInfoById(id);
    }

    @PUT
    @Path("{id}/default")
    public void setDefaultCollection(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        var currentUser = currentUserService.currentUser();
        collectionService.setDefaultCollection(id, currentUser);
    }

    @POST
    public CollectionSummaryJson createCollection(CollectionCreateJson json) {
        var currentUser = currentUserService.currentUser();
        return collectionService.createCollection(json, currentUser);
    }

    @PUT
    @Path("{id}")
    public void updateCollection(@PathParam("id") ID<Collection> id, CollectionUpdateJson json) {
        authorizationService.requireCollectionAccess(id);
        collectionService.updateCollection(id, json);
    }

    @DELETE
    @Path("{id}")
    public void deleteCollection(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        collectionService.deleteCollection(id);
    }
}
