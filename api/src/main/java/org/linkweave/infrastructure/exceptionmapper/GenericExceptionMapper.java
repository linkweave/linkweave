package org.linkweave.infrastructure.exceptionmapper;

import java.sql.SQLException;

import org.linkweave.infrastructure.db.SqliteBusy;
import org.linkweave.infrastructure.errorhandling.AppFailureMessage;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.errorhandling.json.AppFailureErrorJson;
import org.linkweave.infrastructure.i18n.translations.TL;

import io.quarkus.runtime.util.ExceptionUtil;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static org.linkweave.infrastructure.errorhandling.AppFailureMessage.internalError;

@Provider
@Slf4j
@RequiredArgsConstructor
class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private final TL tl;

    @Context
    Providers providers;

    /**
     * Seconds the client is asked to wait before retrying a contended request. Short on purpose:
     * contention clears in milliseconds once the lock holder commits, and the client's own
     * automatic retry (BR-109-9) should feel like a hiccup rather than an outage.
     */
    private static final String RETRY_AFTER_SECONDS = "1";

    @Override
    public Response toResponse(Exception exception) {
        // BR-109-1: lock contention on the single SQLite file is an expected transient condition,
        // not an application crash. It is classified here, in the catch-all, because it arrives
        // wrapped in whatever the failing layer threw (see SqliteBusy) and there is no single
        // exception type a dedicated @Provider could bind to. This is also the only hook that
        // covers contention raised *outside* a retry boundary, including the framework-owned
        // identity lookup in front of every request (BR-109-3) — the path that produced the HTTP
        // 500 in the 2026-08-28 incident.
        if (SqliteBusy.isBusy(exception)) {
            return databaseBusyResponse(exception);
        }

        // do not provide too much information to the client as this might be a security issue!
        AppFailureMessage failureMessage = internalError("Unexpected exception");
        var exceptionId = failureMessage.getId();
        // sadly it seems not all exceptions are catched here...
        // cause of this we have to be quite specific which we want
        // https://github.com/quarkusio/quarkus/issues/4031
        LOG.error("unhandled generic exception: {}", exceptionId, exception);

        final Throwable rootCause = ExceptionUtil.getRootCause(exception);
        if (rootCause instanceof ConstraintViolationException cve) {
            return providers.getExceptionMapper(ConstraintViolationException.class).toResponse(cve);
        }
        org.hibernate.exception.ConstraintViolationException hibernateEx =
            ExceptionUtils.throwableOfType(exception, org.hibernate.exception.ConstraintViolationException.class);
        // ForeignKey Constraints
        if (rootCause instanceof SQLException ex && ex.getMessage().contains("violates foreign key")) {
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
            .entity(translatedBody(failureMessage))
            .build();
    }

    private AppFailureErrorJson translatedBody(AppFailureMessage failureMessage) {
        return new AppFailureErrorJson(failureMessage.getId(),
            tl.translate(failureMessage.getI18nMessage()));
    }

    /**
     * 503 with {@code Retry-After}, and <b>one</b> WARN line.
     *
     * <p>Deliberately not {@code LOG.error(..., exception)}: the incident that motivated UC-109
     * was diagnosed from a wall of identical stack traces, which proved only that something had
     * failed. The root-cause message plus the failure id is what an operator can act on, and
     * BR-109-6's counter — not the log — is where the rate belongs.</p>
     */
    private Response databaseBusyResponse(Exception exception) {
        AppFailureMessage failureMessage = AppFailureMessage.databaseBusy();
        LOG.warn("SQLite lock contention reached the request boundary ({}): answering 503. Cause: {}",
            failureMessage.getId(), ExceptionUtil.getRootCause(exception).getMessage());
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(translatedBody(failureMessage))
            .build();
    }
}
