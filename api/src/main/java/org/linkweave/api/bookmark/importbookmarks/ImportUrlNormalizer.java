package org.linkweave.api.bookmark.importbookmarks;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

/**
 * Normalizes a URL for duplicate comparison (UC-096, BR-080). Mirrors the
 * frontend {@code lib/url.ts#normalizeUrl} so the client and server agree on
 * what counts as a duplicate:
 *
 * <ul>
 *   <li>lowercase scheme and host</li>
 *   <li>strip trailing slashes from the path, including a lone root "/" so that
 *       {@code example.com} and {@code example.com/} compare equal</li>
 *   <li>sort query parameters</li>
 *   <li>drop the fragment</li>
 * </ul>
 *
 * <p>Tracking params ({@code utm_*}) are intentionally <em>kept</em> — the TS
 * normalizer does the same. Be conservative: a near-duplicate slipping through
 * is better than a false "duplicate" that hides a wanted bookmark. If utm
 * stripping is ever wanted, change it here and in {@code lib/url.ts} together.
 */
public final class ImportUrlNormalizer {

    private ImportUrlNormalizer() {
    }

    @NonNull
    public static String normalize(@NonNull String url) {
        try {
            URI parsed = URI.create(url.trim());
            String scheme = parsed.getScheme();
            String host = parsed.getHost();
            if (scheme == null || host == null) {
                return url.trim().toLowerCase();
            }

            StringBuilder result = new StringBuilder();
            result.append(scheme.toLowerCase()).append("://").append(host.toLowerCase());
            if (parsed.getPort() != -1) {
                result.append(':').append(parsed.getPort());
            }

            // Use the RAW (still percent-encoded) path and query so normalization
            // is encoding-faithful and byte-for-byte equivalent to the frontend
            // `lib/url.ts` (which operates on the encoded `pathname`/`search`).
            // getPath()/getQuery() would decode, which both diverges from JS and
            // breaks query tokenization when a decoded value contains '&'/'='.
            String path = parsed.getRawPath() == null ? "" : parsed.getRawPath();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            result.append(path);

            String query = parsed.getRawQuery();
            if (query != null && !query.isEmpty()) {
                String sorted = Arrays.stream(query.split("&"))
                    .sorted()
                    .collect(Collectors.joining("&"));
                result.append('?').append(sorted);
            }

            return result.toString();
        } catch (RuntimeException _) {
            return url.trim().toLowerCase();
        }
    }
}
