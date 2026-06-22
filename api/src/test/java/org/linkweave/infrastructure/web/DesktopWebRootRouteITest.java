package org.linkweave.infrastructure.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.nio.file.Files;
import java.nio.file.LinkOption;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Verifies the desktop SPA web-root serving (UC-052). RestAssured's base path is the app
 * root-path ({@code /api}), so SPA assertions override it to {@code ""} to hit the absolute
 * paths the webview uses.
 */
@QuarkusTest
@TestProfile(DesktopWebRootTestProfile.class)
class DesktopWebRootRouteITest {

    @Test
    void shouldServeIndexAtRoot() {
        given().basePath("").redirects().follow(false)
            .get("/")
            .then()
            .statusCode(200)
            .body(containsString(DesktopWebRootTestProfile.INDEX_MARKER));
    }

    @Test
    void shouldServeStaticAsset() {
        given().basePath("").redirects().follow(false)
            .get("/app.js")
            .then()
            .statusCode(200)
            .body(containsString("spa-asset"));
    }

    @Test
    void shouldFallBackToIndexForSpaDeepLink() {
        // A client-side route with no matching file must return index.html so the Vue router
        // can take over on reload — not a 404.
        given().basePath("").redirects().follow(false)
            .get("/collections/123")
            .then()
            .statusCode(200)
            .body(containsString(DesktopWebRootTestProfile.INDEX_MARKER));
    }

    @Test
    void shouldNotServeFilesThroughEscapingSymlink() {
        // A symlink inside the web-root pointing outside it must not leak the target's contents.
        var link = DesktopWebRootTestProfile.WEB_ROOT.resolve(DesktopWebRootTestProfile.ESCAPING_SYMLINK);
        Assumptions.assumeTrue(Files.exists(link, LinkOption.NOFOLLOW_LINKS),
            "platform does not support symlinks");

        // The escape must be rejected and fall through to the SPA fallback (index.html, 200) —
        // asserting the status + index marker proves that, rather than passing trivially because
        // the body happened not to contain the secret.
        given().basePath("").redirects().follow(false)
            .get("/" + DesktopWebRootTestProfile.ESCAPING_SYMLINK)
            .then()
            .statusCode(200)
            .body(containsString(DesktopWebRootTestProfile.INDEX_MARKER))
            .body(not(containsString(DesktopWebRootTestProfile.SECRET_MARKER)));
    }

    @Test
    void shouldNotEscapeWebRootViaPathTraversal() {
        // Percent-encoded "../" so the HTTP client doesn't pre-collapse it. This is the portable
        // equivalent of "/../../../etc/passwd": it targets a secret planted in the web-root's
        // parent dir. The server must clamp the ".." (Vert.x normalizedPath, backed by the
        // containment guard), keeping the request inside the web-root — so the secret is never
        // served and it falls through to the SPA fallback.
        given().basePath("").urlEncodingEnabled(false).redirects().follow(false)
            .get("/%2e%2e/" + DesktopWebRootTestProfile.outsideSecretFileName)
            .then()
            .statusCode(200)
            .body(containsString(DesktopWebRootTestProfile.INDEX_MARKER))
            .body(not(containsString(DesktopWebRootTestProfile.SECRET_MARKER)));
    }

    @Test
    void shouldNotShadowApiRoutes() {
        // /api/ping is a JAX-RS endpoint; the catch-all SPA route must not swallow it.
        given().basePath("").redirects().follow(false)
            .get("/api/ping")
            .then()
            .statusCode(204);
    }
}
