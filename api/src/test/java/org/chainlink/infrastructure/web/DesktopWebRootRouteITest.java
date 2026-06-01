package org.chainlink.infrastructure.web;

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

        given().basePath("").redirects().follow(false)
            .get("/" + DesktopWebRootTestProfile.ESCAPING_SYMLINK)
            .then()
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
