package org.chainlink.infrastructure.exceptionmapper;

import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureMessage;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationException;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.AppValidationMessage;
import ch.dvbern.dvbstarter.infrastructure.errorhandling.json.AppFailureErrorJson;
import ch.dvbern.dvbstarter.shared.i18n.translations.TL;
import io.quarkus.runtime.util.ExceptionUtil;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.postgresql.util.PSQLException;

import static ch.dvbern.dvbstarter.infrastructure.errorhandling.AppFailureMessage.internalError;

@Provider
@Slf4j
@RequiredArgsConstructor
class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private final TL tl;

    @Context
    Providers providers;

    @Override
    public Response toResponse(Exception exception) {
        // do not provide too much information to the client as this might be a security issue!
        AppFailureMessage failureMessage = internalError("Unexpected exception");
        var exceptionId = failureMessage.getId();
        // sadly it seems not all exceptions are catched here...
        // cause of this we have to be quite specific which we want
        // https://github.com/quarkusio/quarkus/issues/4031
        LOG.error("unhandled generic exception: {}", exceptionId, exception);

        var msg = tl.translate(failureMessage.getI18nMessage());

        final Throwable rootCause = ExceptionUtil.getRootCause(exception);
        if (rootCause instanceof ConstraintViolationException cve) {
            return providers.getExceptionMapper(ConstraintViolationException.class).toResponse(cve);
        }
        org.hibernate.exception.ConstraintViolationException hibernateEx =
            ExceptionUtils.throwableOfType(exception, org.hibernate.exception.ConstraintViolationException.class);
        // ForeignKey Constraints
        if (rootCause instanceof PSQLException ex && ex.getMessage().contains("violates foreign key")) {
            var appValidationException = new AppValidationException(AppValidationMessage.referencedForeignKey());
            return providers.getExceptionMapper(AppValidationException.class).toResponse(appValidationException);
        }
        // UniqueKey Constraint sind im HibernateConstraintViolationExceptionMapper behandelt
        // Manchmal (je nachdem, ob innerhalb der Save-Methode schon geflusht wird oder nicht)
        // wird die Exeption in eine andere Exception gewrappt
        if (hibernateEx != null) {
            return providers.getExceptionMapper(org.hibernate.exception.ConstraintViolationException.class).toResponse(hibernateEx);
        }

        return Response.serverError()
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new AppFailureErrorJson(exceptionId, msg))
            .build();
    }
}
