package org.linkweave.api.ping;

import java.time.temporal.ChronoUnit;

import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.annotation.security.PermitAll;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.linkweave.infrastructure.stereotypes.JaxResource;

@JaxResource
@Path("/ping")
@PermitAll
@Transactional(TxType.NOT_SUPPORTED)
@RateLimit(value = 1200, window = 1, windowUnit = ChronoUnit.MINUTES)
public class PingResource {

    @GET
    @PermitAll
    public Response ping() {
        return Response.noContent().build();
    }
}
