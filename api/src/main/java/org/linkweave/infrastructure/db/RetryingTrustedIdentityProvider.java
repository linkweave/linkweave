package org.linkweave.infrastructure.db;

import java.util.concurrent.atomic.AtomicLong;


import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

/**
 * Puts the per-request identity lookup under the {@code SQLITE_BUSY} retry boundary (BR-109-3).
 *
 * <p>This is the path the 2026-08-28 incident failed on: {@code select … from User where email=?},
 * run by the {@code quarkus-security-jpa} provider generated from {@code @UserDefinition}, in
 * front of <em>every</em> authenticated request. Contention there does not fail one save, it fails
 * everything at once, and it reached the user as an unhandled 500.
 *
 * <p>It cannot be covered the usual way. {@code @RetryOnSqliteBusy} is an interceptor binding, so
 * it needs a method of ours to intercept, and this method is generated at build time from an
 * annotation on the entity. A CDI decorator is the one hook that applies to a bean we do not
 * write: it wraps the generated provider while leaving {@code @UserDefinition} as the single
 * source of truth for how a user is authenticated.
 *
 * <p>Lives in {@code infrastructure.db} rather than next to the auth resources: its subject is
 * database contention, not authentication, and the layering rule reserves
 * {@code infrastructure.db} for exactly this kind of cross-cutting technical concern. It sits
 * beside the retry policy it applies.
 *
 * <p>Deliberately narrow. It adds retry and nothing else — no credential handling, no identity
 * construction, no role mapping — because this sits on the authentication path of every request
 * and the generated provider remains the only thing that decides who the caller is.
 */
@Priority(1)
@Decorator
public class RetryingTrustedIdentityProvider
    implements IdentityProvider<TrustedAuthenticationRequest> {

    /**
     * Counts decorated lookups, so a test can prove this decorator is actually in the chain.
     *
     * <p>Not diagnostics for their own sake. Whether CDI decoration reaches a bean that
     * {@code quarkus-security-jpa} generates at build time is a property of the extension, not of
     * this code, and the failure mode is silent: if ArC ever stops applying the decorator, every
     * request authenticates exactly as before and the retry simply is not there. A behavioural
     * test cannot see that without inducing real lock contention, so the wiring is asserted
     * directly ({@code RetryingTrustedIdentityProviderITest}).</p>
     */
    static final AtomicLong DECORATED_LOOKUPS = new AtomicLong();

    private final IdentityProvider<TrustedAuthenticationRequest> delegate;

    /**
     * Constructor-injected rather than a {@code @Inject @Delegate} field, because field injection
     * is rejected project-wide ({@code GeneralTest#no_field_injection}). CDI accepts the delegate
     * as a constructor parameter, so the rule costs nothing here.
     */
    @Inject
    RetryingTrustedIdentityProvider(
        @Delegate IdentityProvider<TrustedAuthenticationRequest> delegate
    ) {
        this.delegate = delegate;
    }

    /**
     * Implemented rather than left abstract on purpose: ArC's {@code DecoratorGenerator} fails to
     * build an abstract decorator for this interface — "Duplicate method added:
     * getRequestType()" — because it tries to synthesise the generic method twice. Delegating it
     * explicitly keeps the decorator concrete and sidesteps that generation path entirely.
     */
    @Override
    public Class<TrustedAuthenticationRequest> getRequestType() {
        return delegate.getRequestType();
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
        TrustedAuthenticationRequest request,
        AuthenticationRequestContext context
    ) {
        DECORATED_LOOKUPS.incrementAndGet();
        return SqliteBusyRetries.retryWhileBusy(delegate.authenticate(request, context));
    }
}
