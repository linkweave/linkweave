package org.linkweave.infrastructure.db;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.security.identity.IdentityProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one promise both identity decorators make: they add {@code SQLITE_BUSY} retry and
 * change nothing else about authentication (BR-109-3).
 *
 * <p>A CDI decorator keeps that promise by forwarding, and a method it does not declare is a method
 * it answers itself out of {@link IdentityProvider}'s defaults — discarding whatever the delegate
 * said. That failure is silent: every request still authenticates, so no other test here notices.
 * {@code priority()} is the live example, and it is not inert — the manager sorts the providers
 * registered for a request type by it.
 *
 * <p>So this asserts the decorators cover the decorated type's whole surface, and it is really a
 * test about upgrades: when a future Quarkus adds a method to {@code IdentityProvider} — the move
 * to the 3.40 LTS crosses four minor releases of the security layer — this fails and names it,
 * instead of the decorator quietly starting to answer for the delegate. It is a plain unit test on
 * purpose, so the warning arrives from an unattended CI run and not from a flag someone has to
 * remember to set.
 */
class RetryingIdentityProviderDecoratorTest {

    @ParameterizedTest
    @ValueSource(classes = {RetryingTrustedIdentityProvider.class, RetryingApiKeyIdentityProvider.class})
    void shouldForwardEveryMethodOfTheDecoratedType(Class<?> decorator) {
        assertThat(notForwardedBy(decorator))
            .as("%s must declare every IdentityProvider method, so that it forwards to the "
                + "delegate instead of answering from an interface default", decorator.getSimpleName())
            .isEmpty();
    }

    /**
     * Names of {@link IdentityProvider} methods the decorator does not declare.
     *
     * <p>Matched by name rather than by signature: generics erase {@code authenticate(T, …)} to
     * {@code authenticate(AuthenticationRequest, …)} on the interface while the decorator declares
     * the bound type, so the erased signatures never line up. {@code IdentityProvider} overloads
     * nothing, so the name is enough to tell its methods apart.
     */
    private Set<String> notForwardedBy(Class<?> decorator) {
        Set<String> declared = Arrays.stream(decorator.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .map(Method::getName)
            .collect(Collectors.toSet());
        return Arrays.stream(IdentityProvider.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .map(Method::getName)
            .filter(name -> !declared.contains(name))
            .collect(Collectors.toSet());
    }
}
