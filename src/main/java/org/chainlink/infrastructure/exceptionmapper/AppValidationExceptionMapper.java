package org.chainlink.infrastructure.exceptionmapper;

import java.util.List;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationException;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.AppValidationErrorJson;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.ViolationJson;
import ch.dvbern.dvbstarter.shared.i18n.translations.TL;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
class AppValidationExceptionMapper implements ExceptionMapper<AppValidationException> {

    // either quarkus compiler complains (when using constuctor with @Inject) or IntelliJ-Inspections
    // I decided to soothe the compiler :)
    @SuppressWarnings({ "ProtectedField", "java:S6813" })
    @Inject
    protected TL tl;

    private Response responseFromException(AppValidationException exception) {
        var message = tl.translate(exception.getI18nMessage());

        AppValidationErrorJson response =
            new AppValidationErrorJson(List.of(
                ViolationJson.fromReferenceList(
                    message,
                    exception.getClientKey(),
                    List.of()
                )
            ));

        return JsonMappingExceptionMapper.buildValidationErrorResponse(response);
    }

    @Override
    public Response toResponse(AppValidationException exception) {

        var response = responseFromException(exception);
        return response;
    }
}
