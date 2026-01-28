package org.chainlink.infrastructure.errorhandling;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Getter
public final class AppValidationException extends AppException {
    private static final long serialVersionUID = -6978804033368571677L;

    private final String clientKey;

    public AppValidationException(@NonNull AppValidationMessage message) {
        this(message, null);
    }

    public AppValidationException(@NonNull AppValidationMessage message, @Nullable Throwable cause) {
        super(message.getI18nMessage(), ExceptionId.random(), cause);
        this.clientKey = message.getClientKey();
    }
}
