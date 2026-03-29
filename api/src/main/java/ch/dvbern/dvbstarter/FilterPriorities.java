package ch.dvbern.dvbstarter;

import jakarta.ws.rs.Priorities;
import lombok.experimental.UtilityClass;

/**
 * This class exists because annotation params require literal expressions at compile time.
 * Having {@code private static final FOO = a + b;} in a class where this expression is used does not count :(
 */
@UtilityClass
public class FilterPriorities {
    public static final int BEFORE_EVERYTHING = 1;
    public static final int USER_PRINCIPAL = Priorities.AUTHENTICATION;
    // must be *after* USER_PRINCIPAL because the UserPrincipal is required in LoggingInit
    public static final int LOGGING_INIT = USER_PRINCIPAL + 100;
}
