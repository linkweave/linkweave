package org.chainlink.api.ping;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.chainlink.infrastructure.stereotypes.JaxResource;

@JaxResource
@Path("/ping")
@PermitAll
public class PingResource {

    @GET
    public Response ping() {
        return Response.noContent().build();
    }
}
