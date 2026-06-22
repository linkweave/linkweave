package org.linkweave.infrastructure.errorhandling;

import java.io.Serial;

import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import org.jspecify.annotations.NonNull;

public final class AppAuthorizationException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AppAuthorizationException(@NonNull I18nMessage message) {
        super(message, ExceptionId.random(), null);
    }
}
