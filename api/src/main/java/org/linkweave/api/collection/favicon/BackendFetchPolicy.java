package org.linkweave.api.collection.favicon;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.shared.config.ConfigService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Single source of truth for "the backend must not fetch this host", applied
 * uniformly to favicon and screenshot fetches. A host is blocked when it is on:
 * <ul>
 *   <li>the operator-wide backend fetch denylist
 *       ({@code linkweave.fetch.skip-domains}) — hosts the server cannot reach
 *       or should not hammer; or</li>
 *   <li>the bookmark's per-collection browser fetch allowlist — hosts loaded
 *       directly by the user's browser, so the server can't reach them.</li>
 * </ul>
 *
 * <p>Both the favicon read path and the screenshot capture path consult this
 * before any fetch, so blocked hosts are never fetched and never written to the
 * negative cache, on every path (scheduled job and user-initiated refresh
 * alike).
 *
 * <p>Parsed per-collection allowlists are memoized by raw string so the parse
 * runs once per distinct value rather than per favicon read / per capture
 * candidate. The denylist is a single value, already memoized in
 * {@link ConfigService#getBackendFetchDenylist()}.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class BackendFetchPolicy {

    private final ConfigService configService;
    private final ConcurrentHashMap<String, BrowserFetchAllowlist> allowlistCache = new ConcurrentHashMap<>();

    public boolean blocks(@NonNull String host, @Nullable String collectionBrowserAllowlistRaw) {
        return configService.getBackendFetchDenylist().matches(host)
            || allowlistFor(collectionBrowserAllowlistRaw).matches(host);
    }

    private @NonNull BrowserFetchAllowlist allowlistFor(@Nullable String rawCollectionBasedBrowserAllowlist) {
        return allowlistCache.computeIfAbsent(
            rawCollectionBasedBrowserAllowlist == null ?
                "" :
                rawCollectionBasedBrowserAllowlist, BrowserFetchAllowlist::parse);
    }
}
