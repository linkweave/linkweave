package org.linkweave.infrastructure.db;

import java.util.concurrent.atomic.AtomicLong;

import org.linkweave.api.auth.apikey.ApiKeyRequest;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

/**
 * Puts the API-key identity lookup under the {@code SQLITE_BUSY} retry boundary (BR-109-3).
 *
 * <p>BR-109-3's argument is about position, not about which mechanism authenticates: a lookup that
 * runs in front of every request fails everything at once when it fails. That is as true of
 * {@code X-API-Key} traffic — the CLI, the browser extension, the load test — as of a session
 * cookie, so exempting it would have left half the rule unmet. Without this, API-key requests were
 * still classified correctly at the request boundary (503, not 500) but were never retried.
 *
 * <p>Unlike {@link RetryingTrustedIdentityProvider} the delegate here is our own code, so the
 * retry could in principle have gone inside it. It is a decorator anyway, for two reasons: the
 * layering rule reserves {@code infrastructure.db} for callers of this kind, so the policy cannot
 * be reached from {@code api.auth.apikey} in the first place; and keeping both identity paths
 * wrapped the same way means the next person finds one pattern rather than two.
 *
 * <p>Re-executing the lookup is safe. It reads the key by hash and then records a last-used
 * timestamp — a write, and therefore the part worth checking — but a retry only happens after the
 * attempt failed and rolled back, and re-stamping an unchanged value is idempotent.
 */
@Priority(1)
@Decorator
public class RetryingApiKeyIdentityProvider implements IdentityProvider<ApiKeyRequest> {

    /** Test-only, as {@link RetryingTrustedIdentityProvider#DELEGATE_SUBSCRIPTIONS}. */
    static final AtomicLong DELEGATE_SUBSCRIPTIONS = new AtomicLong();

    private final IdentityProvider<ApiKeyRequest> delegate;

    @Inject
    RetryingApiKeyIdentityProvider(@Delegate IdentityProvider<ApiKeyRequest> delegate) {
        this.delegate = delegate;
    }

    /** Delegated explicitly: ArC cannot generate an abstract decorator for this interface. */
    @Override
    public Class<ApiKeyRequest> getRequestType() {
        return delegate.getRequestType();
    }

    /** Forwarded for the reason given on {@link RetryingTrustedIdentityProvider#priority()}. */
    @Override
    public int priority() {
        return delegate.priority();
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
        ApiKeyRequest request,
        AuthenticationRequestContext context
    ) {
        return SqliteBusyRetries.retryWhileBusy(
            delegate.authenticate(request, context)
                .onSubscription().invoke(ignored -> DELEGATE_SUBSCRIPTIONS.incrementAndGet()));
    }
}
