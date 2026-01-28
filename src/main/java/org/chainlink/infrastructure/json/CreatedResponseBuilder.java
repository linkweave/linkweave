package org.chainlink.infrastructure.json;

import ch.dvbern.dvbstarter.shared.types.id.ID;
import ch.dvbern.oss.hemed.esc.api.shared.abstractentity.AbstractEntity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@With
public class CreatedResponseBuilder<T extends AbstractEntity<T>> {

    private final ID<T> id;
    private final Class<?> resourceClass;
    @SuppressWarnings({ "java:S1170", "java:S1075" } /* this is a Builder! */)
    private final String resourcePath = "/{id}";

    public static <T extends AbstractEntity<T>> CreatedResponseBuilder<T> of(
        ID<T> id,
        Class<?> resourceClass
    ) {
        return new CreatedResponseBuilder<>(id, resourceClass);
    }

    public Response build() {
        var uri = UriBuilder.fromResource(resourceClass)
            .path(resourcePath)
            .build(id.getUUID());

        Response response = Response.status(Status.CREATED)
            .location(uri)
            .build();

        return response;
    }
}
