package org.linkweave.api.testutil.wiremock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a {@code WireMockServer} test field to be filled by {@link WireMockLifecycle}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectWireMock {
}
