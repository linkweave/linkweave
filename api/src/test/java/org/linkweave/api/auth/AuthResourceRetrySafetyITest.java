package org.linkweave.api.auth;

import java.util.List;
import java.util.stream.StreamSupport;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.DefaultValuesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the configuration that makes {@code AuthResource}'s class-level {@code @RetryOnSqliteBusy}
 * safe (BR-109-3).
 *
 * <p>{@code /logout} and {@code DELETE /me} clear the session <em>before</em> the commit, so a
 * commit-time rollback does re-run that work. The class comment argues this is harmless, and the
 * argument rests entirely on what {@code OidcSession.logout()} does: with the default cookie-based
 * {@code TokenStateManager} it only rewrites response headers, so a second call is a no-op.
 *
 * <p>That is a property of configuration, not of code. Turn on RP-initiated or back-channel logout
 * ({@code quarkus.oidc.logout.*}) or token revocation and the same method starts making upstream
 * calls — at which point re-executing it stops being free and the class-level retry becomes
 * silently unsafe. Nothing in a properties file would point the author at that comment, so the
 * assumption is asserted here instead of trusted.
 *
 * <p>Matched by prefix rather than by an enumerated list of keys, so a key this test's author did
 * not know about is still caught. Defaults are excluded deliberately: Quarkus enumerates the whole
 * OIDC logout surface whether it is used or not, and a key that only carries its own default
 * (`logout-mode`, the back-channel token cache) configures nothing. What matters is whether
 * something in this repository or the environment has set one.
 *
 * <p>If this fails, the fix is not to delete the assertion: it is to move the retry off the methods
 * that log out, or to prove the remote call is idempotent.
 */
@QuarkusTest
class AuthResourceRetrySafetyITest {

    @Test
    void shouldNotConfigureOidcLogoutOrRevocationWhileLogoutIsRetryable() {
        // ARRANGE
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        // ACT
        List<String> configured = StreamSupport.stream(config.getPropertyNames().spliterator(), false)
            .filter(name -> name.startsWith("quarkus.oidc."))
            .filter(name -> name.contains(".logout.") || name.contains(".revocation."))
            .filter(name -> isSetSomewhereOtherThanDefaults(config, name))
            .sorted()
            .toList();

        // ASSERT
        assertThat(configured)
            .as("AuthResource retries /logout and DELETE /me, which is only safe while "
                + "OidcSession.logout() stays a response-header rewrite; these keys make it issue "
                + "upstream calls, so re-execution would repeat them")
            .isEmpty();
    }

    private boolean isSetSomewhereOtherThanDefaults(SmallRyeConfig config, String name) {
        ConfigValue value = config.getConfigValue(name);
        return value.getValue() != null
            && !value.getValue().isBlank()
            && !DefaultValuesConfigSource.NAME.equals(value.getConfigSourceName());
    }
}
