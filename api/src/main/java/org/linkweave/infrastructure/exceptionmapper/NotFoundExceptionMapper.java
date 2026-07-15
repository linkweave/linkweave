package org.linkweave.infrastructure.exceptionmapper;

import org.linkweave.infrastructure.errorhandling.ExceptionId;
import org.linkweave.infrastructure.errorhandling.json.AppFailureErrorJson;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps {@link NotFoundException} (missing entities on single-resource
 * endpoints, unmatched routes) to a JSON 404. Without this, the rethrow in
 * {@link WebApplicationExceptionMapper} surfaces as a 500.
 */
@Provider
@Slf4j
class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException exception) {
        LOG.debug("Not found: {}", exception.getMessage());

        // Fixed client-facing summary: framework-thrown NotFoundExceptions
        // (unmatched routes, failed path-param conversion) carry messages with
        // internal details; the specifics stay in the log line above.
        return Response.status(Status.NOT_FOUND)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new AppFailureErrorJson(ExceptionId.random(), "Resource not found"))
            .build();
    }
}
