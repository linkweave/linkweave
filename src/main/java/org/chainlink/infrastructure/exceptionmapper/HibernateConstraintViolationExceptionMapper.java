package org.chainlink.infrastructure.exceptionmapper;

import org.chainlink.infrastructure.errorhandling.AppValidationException;
import org.chainlink.infrastructure.errorhandling.AppValidationMessage;
import ch.dvbern.dvbstarter.i18n.translations.TL;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;

@Provider
@Slf4j
@RequiredArgsConstructor
class HibernateConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private final TL tl;

    @Context
    Providers providers;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String constraintName = exception.getConstraintName();
        AppValidationMessage message = null;
        if (constraintName != null) {
            var translatedErrorMsg = tl.translate("AppValidation." + constraintName);
            if (!translatedErrorMsg.startsWith("!")) {
                message = AppValidationMessage.genericMessage("AppValidation." + constraintName);
            } else {
                message = AppValidationMessage.uniqueKeyViolation(constraintName);
            }
        } else {
            message = AppValidationMessage.uniqueKeyViolation("unknown_constraint");
        }
        var appValidationException = new AppValidationException(message);
        return providers.getExceptionMapper(AppValidationException.class).toResponse(appValidationException);
    }
}
