package org.chainlink.infrastructure.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Activates {@link DesktopWebRootRoute} by pointing {@code chainlink.desktop.web-root} at a
 * throwaway directory containing a stand-in SPA ({@code index.html} + one asset). Mirrors how the
 * desktop bundle (UC-052) sets {@code CHAINLINK_DESKTOP_WEB_ROOT}.
 */
public class DesktopWebRootTestProfile implements QuarkusTestProfile {

    static final String INDEX_MARKER = "spa-index-marker";
    static final String ASSET_BODY = "console.log('spa-asset');";
    static final String SECRET_MARKER = "TOP-SECRET-OUTSIDE-WEBROOT";
    /** A symlink inside the web-root that escapes to {@link #SECRET_MARKER} — must NOT be served. */
    static final String ESCAPING_SYMLINK = "escape-link";
    static final Path WEB_ROOT = createWebRoot();

    private static Path createWebRoot() {
        try {
            Path dir = Files.createTempDirectory("desktop-web-root-test");
            dir.toFile().deleteOnExit();
            Files.writeString(dir.resolve("index.html"),
                "<!doctype html><title>SPA</title><body data-marker=\"" + INDEX_MARKER + "\">");
            Files.writeString(dir.resolve("app.js"), ASSET_BODY);

            // Plant a secret file OUTSIDE the web-root and a symlink inside it pointing there, to
            // verify the symlink-escape guard. If the platform forbids symlinks, skip silently.
            Path secret = Files.createTempFile("desktop-secret", ".txt");
            secret.toFile().deleteOnExit();
            Files.writeString(secret, SECRET_MARKER);
            try {
                Files.createSymbolicLink(dir.resolve(ESCAPING_SYMLINK), secret);
            } catch (IOException | UnsupportedOperationException ignored) {
                // No symlink support (e.g. some Windows setups) — the symlink test will be a no-op.
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        // Reproduce the %desktop build profile: serve the SPA at / (root-path=/) while the REST
        // API stays under /api (rest.path=/api). Quarkus re-augments per test profile, so these
        // build-time properties take effect for this test.
        return Map.of(
            "chainlink.desktop.web-root", WEB_ROOT.toString(),
            "quarkus.http.root-path", "/",
            "quarkus.rest.path", "/api");
    }
}
