package org.chainlink.infrastructure.errorhandling;

import java.io.Serial;

import ch.dvbern.oss.commons.i18nl10n.I18nMessage;

public final class AppAuthException extends AppException {

    @Serial
    private static final long serialVersionUID = -1104206699839600550L;

    public AppAuthException() {
        super(
            I18nMessage.of("AppValidation.NO_USER_LOGGED_IN"),
            ExceptionId.fromString("NoUserLoggedIn"),
            null
        );
    }
}
