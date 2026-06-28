package org.linkweave.infrastructure.exceptionmapper;

import org.linkweave.infrastructure.i18n.translations.TL;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.infrastructure.errorhandling.AppAuthorizationException;
import org.linkweave.infrastructure.errorhandling.json.AppFailureErrorJson;

@Provider
@Slf4j
@RequiredArgsConstructor
class AppAuthorizationExceptionMapper implements ExceptionMapper<AppAuthorizationException> {

    private final TL tl;

    @Override
    public Response toResponse(AppAuthorizationException exception) {
        LOG.warn("Authorization denied: {}", exception.getId(), exception);
        var msg = tl.translate(exception.getI18nMessage());

        return Response.status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new AppFailureErrorJson(exception.getId(), msg))
            .build();
    }
}
