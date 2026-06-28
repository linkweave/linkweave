package org.linkweave.infrastructure;

import jakarta.ws.rs.Priorities;
import lombok.experimental.UtilityClass;

/**
 * JAX-RS filter ordering constants. Exists as a class because annotation
 * parameters require compile-time constant expressions.
 */
@UtilityClass
public class FilterPriorities {
    public static final int BEFORE_EVERYTHING = 1;
    public static final int USER_PRINCIPAL = Priorities.AUTHENTICATION;
    // must be *after* USER_PRINCIPAL because the UserPrincipal is required in LoggingInit
    public static final int LOGGING_INIT = USER_PRINCIPAL + 100;
}
