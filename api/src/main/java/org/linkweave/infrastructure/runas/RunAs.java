package org.linkweave.infrastructure.runas;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * Run-as functionality for Quarkus: execute an intercepted method under a synthetic
 * security identity with the given username and roles.
 *
 * @see <a href="https://github.com/quarkusio/quarkus/issues/11392">quarkus#11392</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding
public @interface RunAs {
    @Nonbinding String username();
    @Nonbinding String[] roles();
}
