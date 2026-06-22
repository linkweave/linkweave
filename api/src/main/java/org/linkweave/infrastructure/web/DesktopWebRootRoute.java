package org.linkweave.infrastructure.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.shared.config.ConfigService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Serves the built Vue SPA from a filesystem directory, but <strong>only</strong> when
 * {@code LINKWEAVE_DESKTOP_WEB_ROOT} (config key {@code linkweave.desktop.web-root}) is set.
 *
 * <p>This exists solely for the desktop bundle (UC-052), where there is no Caddy in front of
 * the backend: the Tauri shell ships the built {@code dist/} folder next to the jar, points
 * this property at it, and loads {@code http://127.0.0.1:<port>} in its webview. Everything is
 * then same-origin, so session cookies and the relative {@code /api/...} calls work unchanged.
 *
 * <p>In the normal Docker deployment the property is unset, this bean registers nothing, and
 * Caddy keeps serving the frontend and proxying {@code /api}. The runtime directory is supplied
 * via {@code LINKWEAVE_DESKTOP_WEB_ROOT}, mirroring {@code LINKWEAVE_DB_PATH} and
 * {@code LINKWEAVE_FAVICON_CACHE_DIR} — the SPA is never baked into the jar.
 *
 * <p>The SPA must be served at {@code /} (with assets at {@code /assets/...}); serving it under
 * {@code /api} would collide with the REST API on deep-link reloads. The desktop build profile
 * ({@code %desktop} in {@code application.properties}) makes this possible by setting
 * {@code quarkus.http.root-path=/} and {@code quarkus.rest.path=/api}: the API stays under
 * {@code /api} while {@code /} is free for the SPA. This handler then serves it from there.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DesktopWebRootRoute {

    // Run early enough to serve assets before the request reaches the REST / non-application
    // handlers, yet pass through (next) for those paths so the backend still handles them.
    private static final int FILTER_PRIORITY = 100;
    private static final String INDEX_PAGE = "index.html";

    private final ConfigService configService;

    // Backend path prefixes, read from their own config so this stays correct if they change.
    // In the desktop profile these resolve to "/api" and "/q" respectively.
    @ConfigProperty(name = "quarkus.rest.path", defaultValue = "/")
    String restPath;

    @ConfigProperty(name = "quarkus.http.non-application-root-path", defaultValue = "/q")
    String nonApplicationRootPath;

    void registerWebRoot(@Observes Filters filters) {
        var webRoot = configService.getDesktopWebRoot();
        if (webRoot.isEmpty() || webRoot.get().isBlank()) {
            return;
        }
        // toRealPath() resolves symlinks in the root itself and fails fast if it is missing, so the
        // canonical root is a sound base for the traversal checks below.
        Path rootDir;
        try {
            rootDir = Path.of(webRoot.get()).toRealPath();
        } catch (IOException e) {
            LOG.warn("Desktop web-root '{}' is missing or unreadable; SPA will not be served", webRoot.get(), e);
            return;
        }
        LOG.info("Desktop mode: serving SPA from web-root '{}'", rootDir);

        var staticHandler = StaticHandler.create(FileSystemAccess.ROOT, rootDir.toString())
            .setIndexPage(INDEX_PAGE);

        filters.register(ctx -> handle(ctx, rootDir, staticHandler), FILTER_PRIORITY);
    }

    private void handle(RoutingContext ctx, Path rootDir, StaticHandler staticHandler) {
        var path = ctx.normalizedPath();
        var isBackend = path.startsWith(restPath) || path.startsWith(nonApplicationRootPath);
        if (ctx.request().method() != HttpMethod.GET || isBackend) {
            ctx.next();
            return;
        }

        var relative = path.equals("/") ? INDEX_PAGE : path.substring(1);
        var requested = rootDir.resolve(relative).normalize();

        // Lexical path-traversal guard: reject anything that resolves outside the web-root
        // (normalizedPath already clamps "..", this is belt-and-suspenders).
        if (!requested.startsWith(rootDir)) {
            ctx.next();
            return;
        }

        if (Files.isRegularFile(requested) && isWithinRoot(requested, rootDir)) {
            // Real asset (and "/" -> index.html): let StaticHandler serve it with the right
            // content-type, caching and range support. Note StaticHandler re-derives the file from
            // ctx.normalizedPath() independently of `requested` above; both start from the same
            // normalized path, and the isWithinRoot (toRealPath) check is the authoritative
            // containment guard regardless of which resolution ultimately serves the bytes.
            staticHandler.handle(ctx);
        } else {
            // SPA deep-link (e.g. /collections/123): serve index.html so the Vue router can
            // take over on reload, rather than 404-ing.
            ctx.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .sendFile(rootDir.resolve(INDEX_PAGE).toString());
        }
    }

    /**
     * Symlink-safe containment check: resolves the real (symlink-followed) path and confirms it is
     * still inside the web-root, so a symlink in the bundle cannot escape to serve arbitrary files.
     */
    private boolean isWithinRoot(Path file, Path rootDir) {
        try {
            return file.toRealPath().startsWith(rootDir);
        } catch (IOException e) {
            return false;
        }
    }
}
