package org.chainlink.infrastructure.exceptionmapper;

import org.chainlink.infrastructure.errorhandling.ExceptionId;
import org.chainlink.infrastructure.errorhandling.json.AppFailureErrorJson;
import ch.dvbern.dvbstarter.i18n.translations.TL;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@RequiredArgsConstructor
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    private final TL tl;

    @Context
    private UriInfo uriInfo;
    @Context
    private HttpServerRequest request;

    @Override
    public Response toResponse(
        WebApplicationException exception
    ) {
        if (exception.getCause() instanceof JsonMappingException jme) {
            return new JsonMappingExceptionMapper(tl).toResponse(jme);
        }
        if (exception.getCause() instanceof JsonParseException jpe) {
            return new JsonParseExceptionMapper(tl).toResponse(jpe);
        }
        if (exception instanceof ClientErrorException) {
            return buildClientErrorResponse(exception);
        }

        throw exception;
    }

    private Response buildClientErrorResponse(WebApplicationException exception) {
        ExceptionId exceptionId = ExceptionId.random();
        var message = "%s (URL: %s %s)".formatted(
            exception.getMessage(),
            request.method().name(),
            uriInfo.getRequestUri().toString()
        );

        LOG.debug(message, exception);

        return Response.status(Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new AppFailureErrorJson(exceptionId, message))
            .build();
    }
}
