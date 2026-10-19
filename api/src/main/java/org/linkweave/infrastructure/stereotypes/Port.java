package org.linkweave.infrastructure.stereotypes;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a driven (outbound) port: an interface the application defines for
 * something it needs from the outside, implemented by an adapter.
 *
 * <p>Outbound only, for now. Inbound boundaries are the public API of a service
 * class — a REST resource, a scheduled job and a test all drive the same service
 * — and those have no interface to annotate.
 *
 * <p>Not a CDI stereotype: it carries no scope and no transaction semantics, and
 * exists purely so {@code PortsAndAdaptersTest} can tell ports and adapters apart.
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
public @interface Port {
}
