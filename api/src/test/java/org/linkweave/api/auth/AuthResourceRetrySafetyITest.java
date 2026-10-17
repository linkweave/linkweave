package org.linkweave.api.auth;

import java.util.List;
import java.util.stream.StreamSupport;

import org.linkweave.api.shared.archunit.ArchConst;
import io.quarkus.oidc.TokenStateManager;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.DefaultValuesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Pins the assumptions that make {@code AuthResource}'s class-level {@code @RetryOnSqliteBusy} safe
 * on {@code /logout} and {@code DELETE /me} (BR-109-3).
 *
 * <p>Both methods clear the session <em>before</em> the commit, so a commit-time rollback does
 * re-run that work. That is harmless only while {@code OidcSession.logout()} stays a rewrite of
 * response headers: it sets {@code Clear-Site-Data}, expires the cookie, and hands any stored token
 * state to the configured {@code TokenStateManager}, which for the default cookie-based one has
 * nothing to delete. Make it do remote work and a retry repeats that call.
 *
 * <p>Two of the ways to do that are silent — they would not take the author anywhere near
 * {@code AuthResource} — so they are asserted here:</p>
 * <ul>
 *   <li><b>RP-initiated, back-channel or front-channel logout</b>, which is pure configuration
 *       under {@code quarkus.oidc.logout.*}. Matched by prefix, so a key this test's author did not
 *       know about is still caught.
 *
 *       <p>Two things make the matching less obvious than it looks. SmallRye reports
 *       <em>raw</em> source names, so a key written as {@code %prod.quarkus.oidc.logout.path} —
 *       the form this repository uses everywhere — does not start with {@code quarkus.oidc.} and
 *       would slip past a naive prefix test, so a leading {@code %<profile>.} segment is stripped
 *       first. And Quarkus enumerates the whole logout surface whether it is used or not, so an
 *       unprofiled key is only interesting when its value comes from something other than
 *       {@code DefaultValuesConfigSource}; a profiled key has no default source, so its mere
 *       presence means someone wrote it.</p></li>
 *   <li><b>A {@code TokenStateManager} of our own</b>, which would be wired by CDI from anywhere in
 *       the codebase. Asserted statically rather than through {@code Arc}, because
 *       {@code %test.quarkus.oidc.enabled=false} removes the OIDC beans and a resolution-based
 *       check would pass by finding nothing.</li>
 * </ul>
 *
 * <p>Token revocation is deliberately <em>not</em> covered, because in quarkus-oidc 3.36.2 it is
 * not reachable by configuration: it means calling
 * {@code OidcProviderClient#revokeAccessToken/revokeRefreshToken}, which would have to be added to
 * this resource — where this comment is. There is no {@code quarkus.oidc} revocation property to
 * guard, so do not go looking for one.
 *
 * <p>If either assertion fails, the fix is not to delete it: it is to move the retry off the
 * methods that log out, or to prove the remote call is idempotent.
 */
@QuarkusTest
class AuthResourceRetrySafetyITest {

    @Test
    void shouldNotConfigureRemoteLogoutWhileLogoutIsRetryable() {
        // ARRANGE
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        // ACT
        List<String> configured = StreamSupport.stream(config.getPropertyNames().spliterator(), false)
            .filter(name -> isRemoteLogoutKey(withoutProfilePrefix(name)))
            .filter(name -> isProfiled(name) || isSetSomewhereOtherThanDefaults(config, name))
            .sorted()
            .toList();

        // ASSERT
        assertThat(configured)
            .as("AuthResource retries /logout and DELETE /me, which is only safe while "
                + "OidcSession.logout() stays a response-header rewrite; these keys make it call "
                + "the provider, so a retry would repeat that call")
            .isEmpty();
    }

    @Test
    void shouldNotReplaceTheCookieBasedTokenStateManagerWhileLogoutIsRetryable() {
        var rule = noClasses()
            .should()
            .implement(TokenStateManager.class)
            .because("the default cookie-based TokenStateManager has nothing to delete on logout, "
                + "which is what makes AuthResource's retry of /logout and DELETE /me a no-op the "
                + "second time; a server-side one would do real work per call");

        rule.check(ArchConst.APP_CLASSES);
    }

    private boolean isRemoteLogoutKey(String name) {
        return name.startsWith("quarkus.oidc.") && name.contains(".logout.");
    }

    private boolean isProfiled(String name) {
        return name.startsWith("%");
    }

    /** {@code %prod.quarkus.oidc.logout.path} and {@code %dev,test.…} both lose their prefix. */
    private String withoutProfilePrefix(String name) {
        return isProfiled(name) ? name.substring(name.indexOf('.') + 1) : name;
    }

    private boolean isSetSomewhereOtherThanDefaults(SmallRyeConfig config, String name) {
        ConfigValue value = config.getConfigValue(name);
        return value.getValue() != null
            && !value.getValue().isBlank()
            && !DefaultValuesConfigSource.NAME.equals(value.getConfigSourceName());
    }
}
