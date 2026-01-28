package org.chainlink.infrastructure.errorhandling;

import ch.dvbern.dvbstarter.DebugUtil;
import ch.dvbern.oss.commons.i18nl10n.I18nMessage;
import lombok.Getter;
import org.chainlink.infrastructure.types.IgnoreForIdClassTest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Getter
@SuppressWarnings("AbstractClassExtendsConcreteClass")
public abstract class AppException extends RuntimeException {
    private static final long serialVersionUID = -30027164914702837L;

    private final I18nMessage i18nMessage;

    /**
     * An id that uniquely identifies this exception.
     * <br>
     * It is intended to find error messages in (e.g.) logs
     * and might be presented to the user (and thus: provided to telephone-support)
     */
    @IgnoreForIdClassTest
    private final ExceptionId id;

    protected AppException(
        @NonNull I18nMessage message,
        @NonNull ExceptionId id,
        @Nullable Throwable cause
    ) {
        super(buildRawMessage(message, id), cause);
        this.i18nMessage = message;
        this.id = id;
    }

    @NonNull
    private static String buildRawMessage(
        @NonNull I18nMessage i18nMessage,
        @NonNull @IgnoreForIdClassTest ExceptionId id
    ) {
        var result = i18nMessage.key().value();
        if (!i18nMessage.args().isEmpty()) {
            result += ", args: " + DebugUtil.prettyPrintMap(i18nMessage.args());
        }
        result += ", id: " + id;

        return result;
    }

}
