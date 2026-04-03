package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/collections")
public class CollectionResource {

    private final CollectionService collectionService;

    private final AuthorizationService authorizationService;

    @GET
    @Path("{id}")
    public CollectionInfoJson getCollectionById(@PathParam("id") ID<Collection> id) {
        authorizationService.requireCollectionAccess(id);
        return collectionService.getCollectionById(id);
    }
}
