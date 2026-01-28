package org.chainlink.infrastructure.exceptionmapper;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureException;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.AppFailureErrorJson;
import ch.dvbern.dvbstarter.shared.i18n.translations.TL;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@RequiredArgsConstructor
class AppFailureExceptionMapper implements ExceptionMapper<AppFailureException> {

    private final TL tl;

    @Override
    public Response toResponse(AppFailureException exception) {
        LOG.error("AppFailure: {}", exception.getId(), exception);
        var msg = tl.translate(exception.getI18nMessage());

        return Response.serverError()
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new AppFailureErrorJson(exception.getId(), msg))
            .build();
    }
}
