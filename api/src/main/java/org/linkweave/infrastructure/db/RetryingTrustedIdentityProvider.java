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
 *
 * <p>Narrow in scope, too: this covers the {@code TrustedAuthenticationRequest} that resolves a
 * session cookie, not the {@code UsernamePasswordAuthenticationRequest} that
 * {@code /j_security_check} issues once at login. That omission is deliberate — BR-109-3 is about
 * the lookup that runs in front of <em>every</em> request, and contention at login costs one
 * sign-in attempt rather than the whole session. A contended login is still classified (503 with
 * {@code Retry-After}, BR-109-1); it is simply not retried for the caller.
 */
@Priority(1)
@Decorator
public class RetryingTrustedIdentityProvider
    implements IdentityProvider<TrustedAuthenticationRequest> {

    /**
     * Test-only observability: production code never reads this. It counts <em>subscriptions to
     * the delegate</em>, so a test can prove two separate things that are otherwise invisible.
     *
     * <p>First, that the decorator is in the chain at all. Whether CDI decoration reaches a bean
     * that {@code quarkus-security-jpa} generates at build time is a property of the extension,
     * not of this code, and the failure mode is silent: if ArC ever stops applying the decorator,
     * every request authenticates exactly as before and the retry simply is not there.</p>
     *
     * <p>Second, that a retry actually re-runs the lookup. Note this counts subscriptions and not
     * calls to {@link #authenticate}: {@code IdentityProviderManager} invokes the provider once,
     * when it assembles the chain, so the method body runs a single time per authentication while
     * the {@link Uni} beneath it is re-subscribed once per attempt. Counting method entries would
     * report 1 no matter how many times the retry fired.</p>
     */
    static final AtomicLong DELEGATE_SUBSCRIPTIONS = new AtomicLong();

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

    /**
     * Forwarded even though {@link IdentityProvider} supplies a default, so that the decorator
     * never answers for the delegate.
     *
     * <p>A decorator that inherits the default would discard an override the delegate makes, and
     * this value is not inert: {@code QuarkusIdentityProviderManagerImpl.Builder#build()}
     * (quarkus-security 3.36.2) sorts the providers registered for one request type by it,
     * descending, and the first provider to return an identity wins. Dropping an override would
     * therefore reorder authentication, silently.</p>
     *
     * <p>Nothing overrides it today — {@code JpaTrustedIdentityProvider} takes the interface
     * default of {@code SYSTEM_FIRST + 1} (io.quarkus.security:quarkus-security 2.3.2, the version
     * Quarkus 3.36.2 resolves) — so forwarding it now costs nothing, and it is one fewer way for
     * the move to the 3.40 LTS to change behaviour without saying so. With this, all three methods
     * of {@link IdentityProvider} are forwarded explicitly, which is what makes
     * {@code RetryingIdentityProviderDecoratorTest} able to check the claim above rather than
     * restate it.</p>
     */
    @Override
    public int priority() {
        return delegate.priority();
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
        TrustedAuthenticationRequest request,
        AuthenticationRequestContext context
    ) {
        return SqliteBusyRetries.retryWhileBusy(
            delegate.authenticate(request, context)
                .onSubscription().invoke(ignored -> DELEGATE_SUBSCRIPTIONS.incrementAndGet()));
    }
}
