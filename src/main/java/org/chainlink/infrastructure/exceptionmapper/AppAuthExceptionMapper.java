package org.chainlink.infrastructure.exceptionmapper;

import org.chainlink.infrastructure.errorhandling.AppAuthException;
import org.chainlink.infrastructure.errorhandling.json.AppFailureErrorJson;
import ch.dvbern.dvbstarter.i18n.translations.TL;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@RequiredArgsConstructor
class AppAuthExceptionMapper implements ExceptionMapper<AppAuthException> {

    private final TL tl;

    @Override
    public Response toResponse(AppAuthException exception) {
        LOG.error("AppAuthException: {}", exception.getId(), exception);
        var msg = tl.translate(exception.getI18nMessage());

        return Response.status(Response.Status.UNAUTHORIZED)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new AppFailureErrorJson(exception.getId(), msg))
            .build();
    }
}
