package org.chainlink.infrastructure.exceptionmapper;

import java.util.List;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureMessage;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationException;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.ExceptionId;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.AppFailureErrorJson;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.AppValidationErrorJson;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.ViolationJson;
import ch.dvbern.dvbstarter.shared.i18n.translations.TL;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureMessage.internalError;

@Provider
@Slf4j
@RequiredArgsConstructor
class JsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {
    private final TL tl;

    private final ConstraintViolationParser violationParser = new ConstraintViolationParser();

    public static Response buildValidationErrorResponse(AppValidationErrorJson validation) {
        Response result = Response
            .status(Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(validation)
            .build();
        return result;
    }

    @Override
    public Response toResponse(JsonMappingException exception) {

        try {
            Throwable cause = exception.getCause();
            if (cause instanceof ConstraintViolationException cv) {
                List<ViolationJson> violations = violationParser.parse(cv.getConstraintViolations());
                return buildValidationErrorResponse(new AppValidationErrorJson(violations));
            }

            if (cause instanceof AppValidationException av) {
                // this case happens when e.g. the emailaddress is parsed and fails
                // cause of this we get a message from the caused exception but the path from the jsonexception
                return buildValidationErrorResponse(new AppValidationErrorJson(List.of(
                    ViolationJson.fromReferenceList(
                        tl.translate(av.getI18nMessage()),
                        av.getClientKey(),
                        exception.getPath()
                    ))));

            }

            AppFailureMessage failureMessage = AppFailureMessage.jsonMapping();
            ExceptionId exceptionId = failureMessage.getId();
            LOG.error("Unhandled exception: {}", exceptionId, exception);
            var msg = tl.translate(failureMessage.getI18nMessage());
            var detail = JsonMappingReferenceParser.fromRefrenceList(exception.getPath()).parse();

            return buildFailureResponse(
                new AppFailureErrorJson(exceptionId, msg, detail));

        } catch (RuntimeException rte) {
            AppFailureMessage internalErrorMessage = internalError("null");
            ExceptionId exceptionId = internalErrorMessage.getId();
            LOG.error("Error while building the error message: {}", exceptionId, rte);
            var msg = tl.translate(internalErrorMessage.getI18nMessage());
            return buildFailureResponse(new AppFailureErrorJson(
                exceptionId,
                msg,
                "error building the validation error message, see server logfile for details"
            ));
        }

    }

    private Response buildFailureResponse(AppFailureErrorJson failure) {
        return Response
            .serverError()
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(failure)
            .build();

    }

}
