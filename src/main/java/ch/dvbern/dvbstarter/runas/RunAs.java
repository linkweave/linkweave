package ch.dvbern.dvbstarter.runas;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * This can enable a RunAs functionality in Quarkus.
 * <a href="https://github.com/quarkusio/quarkus/issues/11392">...</a>
 * <a href="https://github.com/quarkusio/quarkus-quickstarts/commit/22ce0e79395e291fe700edb2c05029336f225c95">...</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding
public @interface RunAs {
    @Nonbinding String username();
    @Nonbinding String[] roles();
}
