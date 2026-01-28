package org.chainlink.infrastructure.exceptionmapper;

import java.util.stream.Collectors;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureMessage;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.AppFailureErrorJson;
import ch.dvbern.dvbstarter.shared.i18n.translations.TL;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
@RequiredArgsConstructor
class UnrecognizedPropertyExceptionMapper implements ExceptionMapper<UnrecognizedPropertyException> {
    private final TL tl;

    @Override
    public Response toResponse(UnrecognizedPropertyException exception) {
        var knownPoperties = exception.getKnownPropertyIds().stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
        var path = JsonMappingReferenceParser.fromRefrenceList(exception.getPath())
            .parse();

        AppFailureMessage failureMessage = AppFailureMessage.unrecognizedProperty(
            exception.getReferringClass(),
            exception.getPropertyName(),
            path,
            knownPoperties
        );
        var msg = tl.translate(failureMessage.getI18nMessage());

        return Response.status(Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new AppFailureErrorJson(
                failureMessage.getId(),
                msg
            ))
            .build();
    }

}
